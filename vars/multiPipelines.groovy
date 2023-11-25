import java.nio.file.*

/**
 * The step entry point.
 */
def call() {
    String repositoryName = env.JOB_NAME.split('/')[1]
    String rootFolderPath = "Generated/$repositoryName"

    List<String> jenkinsfilePaths = provisionItems(rootFolderPath, env.GIT_URL)
    List<String> multibranchPipelinesToRun = findMultibranchPipelinesToRun(jenkinsfilePaths)
    runPipelines(rootFolderPath, multibranchPipelinesToRun)
}

/**
 * Provision items on Jenkins.
 * @param rootFolderPath A root folder path.
 * @param repositoryURL The repository URL.
 * @return The list of Jenkinsfile paths for which corresponding items have been provisioned.
 */
List<String> provisionItems(String rootFolderPath, String repositoryURL) {
    //Plugins to install
        // 1) Basic Branch Build Strategies Plugin
        // 2) pipeline-utility-steps
        // 3) job-dsl
    // Find all Jenkinsfiles.
    //https://stackoverflow.com/q/50675533
    // https://plugins.jenkins.io/pipeline-utility-steps/
    //https://stackoverflow.com/a/50700189
    //https://stackoverflow.com/questions/70564549/jenkins-pipeline-no-such-dsl-method-jobdsl-found
    //https://stackoverflow.com/questions/43699190/seed-job-asks-for-script-approval-in-jenkins
    // By default, Jenkins notifies GitHub with a constant context, i.e. a string that check multibranch.groovy file line 108
    // identifies the check. We want each individual build result to have its own context so
    // they do not conflict. Requires the github-scm-trait-notification-context-plugin to be
    // installed on the Jenkins instance.
    //https://stackoverflow.com/a/58989575
    //Other security issue do come up for approval
    List<String> jenkinsfilePaths = findFiles(glob: '**/*/Jenkinsfile').collect { it.path }

    // Provision folder and Multibranch Pipelines.
    jobDsl(
            scriptText: libraryResource('multiBranchPipeline.groovy'),
            additionalParameters: [
                    jenkinsfilePathsStr: jenkinsfilePaths,
                    rootFolderStr      : rootFolderPath,
                    repositoryURL      : env.GIT_URL
            ],
            // The following may be set to 'DELETE'. Note that branches will compete to delete and recreate items
            // unless you only provision items from the default branch.
            removedJobAction: 'IGNORE'
    )

    return jenkinsfilePaths
}

/**
 * Get the most relevant baseline revision.
 * @return A revision.
 */
String getBaselineRevision() {
    // Depending on your seed pipeline configuration and preferences, you can set the baseline revision to a target
    // branch, e.g. the repository's default branch or even `env.CHANGE_TARGET` if Jenkins is configured to discover
    // pull requests.
    // Look for the first existing revision. Commits can be removed (e.g. with a `git push --force`), so a
    // previous build revision may not exist anymore.
    //print("CHANGE_TARGET :", env.CHANGE_TARGET)
   // print("GIT_PREVIOUS_SUCCESSFUL_COMMIT:", env.GIT_PREVIOUS_SUCCESSFUL_COMMIT)
   // print("GIT_PREVIOUS_COMMIT:", env.GIT_PREVIOUS_COMMIT)
    [env.GIT_PREVIOUS_SUCCESSFUL_COMMIT, env.GIT_PREVIOUS_COMMIT, env.CHANGE_TARGET]
            .find { revision ->
                revision != null && sh(script: "git rev-parse --quiet --verify $revision", returnStdout: true) == 0
            } ?: 'HEAD^'
}

/**
 * Get the list of changed directories.
 * @param baselineRevision A revision to compare to the current revision.
 * @return The list of directories which include changes.
 */
List<String> getChangedDirectories(String baselineRevision) {
    // Jenkins native interface to retrieve changes, i.e. `currentBuild.changeSets`, returns an empty list for newly
    // created branches (see https://issues.jenkins.io/browse/JENKINS-14138), so let's use `git` instead.
    sh(
            label: 'List changed directories',
            script: "git diff --name-only $baselineRevision | xargs -L1 dirname | uniq",
            returnStdout: true,
    ).split().toList()
}

/**
 * Find Multibranch Pipelines which Jenkinsfile is located in a directory that includes changes.
 * @param changedFilesPathStr List of changed files paths.
 * @param jenkinsfilePathsStr List of Jenkinsfile paths.
 * @return A list of Pipeline names, relative to the repository root.
 */
// `java.nio.file.Path(s)` instances are not serializable, so we have to add the following annotation.
@NonCPS
static List<String> findRelevantMultibranchPipelines(List<String> changedFilesPathStr, List<String> jenkinsfilePathsStr) {
    List<Path> changedFilesPath = changedFilesPathStr.collect { Paths.get(it) }
    List<Path> jenkinsfilePaths = jenkinsfilePathsStr.collect { Paths.get(it) }

    changedFilesPath.inject([]) { pipelines, changedFilePath ->
        def matchingJenkinsfile = jenkinsfilePaths
                .find { jenkinsfilePath -> changedFilePath.startsWith(jenkinsfilePath.parent) }
        matchingJenkinsfile != null ? pipelines + [matchingJenkinsfile.parent.toString()] : pipelines
    }.unique()
}

/**
 * Get the list of Multibranch Pipelines that should be run according to the changeset.
 * @param jenkinsfilePaths The list of Jenkinsfiles paths.
 * @return The list of Multibranch Pipelines to run relative to the repository root.
 */
List<String> findMultibranchPipelinesToRun(List<String> jenkinsfilePaths) {
    findRelevantMultibranchPipelines(getChangedDirectories(baselineRevision), jenkinsfilePaths)
}

/**
 * Run pipelines.
 * @param rootFolderPath The common root folder of Multibranch Pipelines.
 * @param multibranchPipelinesToRun The list of Multibranch Pipelines for which a Pipeline is run.
 */
def runPipelines(String rootFolderPath, List<String> multibranchPipelinesToRun) {
    parallel(multibranchPipelinesToRun.inject([:]) { stages, multibranchPipelineToRun ->
        stages + [("Building $multibranchPipelinesToRun"): {
            def pipelineName = "$rootFolderPath/$multibranchPipelinesToRun/${URLEncoder.encode(env.CHANGE_BRANCH ?: env.GIT_BRANCH, 'UTF-8')}"
            // For new branches, Jenkins will receive an event from the version control system to provision the
            // corresponding Pipeline under the Multibranch Pipeline item. We have to wait for Jenkins to process the
            // event so a build can be triggered.
            timeout(time: 5, unit: 'MINUTES') {
                waitUntil(initialRecurrencePeriod: 1e3) {
                    def pipeline = Jenkins.instance.getItemByFullName(pipelineName)
                    pipeline && !pipeline.isDisabled()
                }
            }

            // Trigger downstream builds.
            build(job: pipelineName, propagate: true, wait: true)
        }]
    })
}
