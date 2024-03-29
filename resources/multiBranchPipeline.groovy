import java.nio.file.*

// `jenkinsfilePathsStr` and `rootFolderStr` are global variables that are set through `jobDsl`'s `additionalParameters`
List<Path> jenkinsfilePaths = jenkinsfilePathsStr.collect { Paths.get(it) }
Path rootFolder = Paths.get(rootFolderStr)

generateFolders(jenkinsfilePaths, rootFolder)
generateMultibranchPipelines(jenkinsfilePaths, rootFolder, repositoryURL)


/**
 * Generate folders.
 * @param jenkinsfilePaths A list of Jenkinsfile paths.
 */
def generateFolders(List<Path> jenkinsfilePaths, Path rootFolder) {
    jenkinsfilePaths
            .collect { ancestorFoldersPath(rootFolder.resolve(it).parent) }
            .flatten()
    // Remove duplicates in case some Jenkinsfiles share ancestors for optimization.
            .unique()
            .each {
                // Effectively provision the folder.
                folder(it.toString())
            }
}

/**
 * Get all ancestor folders paths.
 * <pre>
 *     ancestorFoldersPath(Paths.get('root/parent/child/file'))
 *     // returns [root, root/parent, root/parent/child]
 * </pre>
 * @param path A path.
 * @return All its ancestors paths.
 */
List<Path> ancestorFoldersPath(Path path) {
    if (path.parent == null) return []
    ancestorFoldersPath(path.parent) + [path.parent]
}


/**
 * Generate Multibranch Pipelines.
 * @param repositoryURL The Git repository URL.
 * @param jenkinsfilePaths A list of Jenkinsfiles paths.
 */
def generateMultibranchPipelines(List<Path> jenkinsfilePaths, Path rootFolder, String repositoryURL) {
    // The following variables are needed to configure the branch source for GitHub. Configuration for other version
    // control providers vary.
    def matcher = repositoryURL =~ /.+[\/:](?<owner>[^\/]+)\/(?<repository>[^\/]+)\.git$/
    matcher.matches()
    String repositoryOwner = matcher.group('owner')
    String repositoryName = matcher.group('repository')

    // Discover branches strategies
    final int EXCLUDE_PULL_REQUESTS_STRATEGY_ID = 1

    // Discover pull requests from origin strategies
    final int USE_CURRENT_SOURCE_STRATEGY_ID = 2

    jenkinsfilePaths.each { jenkinsfilePath ->
        String pipelineName = jenkinsfilePath.parent

        multibranchPipelineJob(rootFolder.resolve(pipelineName).toString()) {
            branchSources {
                branchSource {
                    source {
                        github {
                            // We must set a branch source ID.
                            id('github')

                            // repoOwner, repository, repositoryUrl and configuredByUrl are all required
                            repoOwner(repositoryOwner)
                            repository(repositoryName)
                            repositoryUrl(repositoryURL)
                            configuredByUrl(false)

                            // Make sure to properly set this.
                            credentialsId('personal-pat')

                            traits {

                                sparseCheckoutPathsTrait {
                                    extension {
                                        sparseCheckoutPaths {
                                            sparseCheckoutPath {
                                                path("$pipelineName/*")
                                            }
                                        }
                                    }
                                }

                                // Depending on your preferences and root pipeline configuration, you can decide to
                                // discover branches, pull requests, perhaps even tags.
                                gitHubBranchDiscovery {
                                    strategyId(EXCLUDE_PULL_REQUESTS_STRATEGY_ID)
                                }
                                gitHubPullRequestDiscovery {
                                    strategyId(USE_CURRENT_SOURCE_STRATEGY_ID)
                                }

//                                gitHubTagDiscovery()

                                // By default, Jenkins notifies GitHub with a constant context, i.e. a string that
                                // identifies the check. We want each individual build result to have its own context so
                                // they do not conflict. Requires the github-scm-trait-notification-context-plugin to be
                                // installed on the Jenkins instance.
                                notificationContextTrait {
                                    contextLabel("continuous-integration/jenkins/$pipelineName")
                                    typeSuffix(false)
                                }
                            }
                        }
                    }

                    // By default, Jenkins will trigger builds as it detects changes on the source repository. We want
                    // to avoid that since we will trigger child pipelines on our own only when relevant.
                    buildStrategies {
                        skipInitialBuildOnFirstBranchIndexing()
                    }
                    strategy {
                        defaultBranchPropertyStrategy {
                            props {
                                noTriggerBranchProperty()
                            }
                        }
                    }
                }
            }

            factory {
                workflowBranchProjectFactory {
                    scriptPath(jenkinsfilePath.toString())
                }
            }
            orphanedItemStrategy {
                discardOldItems {
                    daysToKeep(3)
                }
            }
            triggers {
                periodicFolderTrigger {
                    interval('5m')
                }
            }
        }
    }
}
