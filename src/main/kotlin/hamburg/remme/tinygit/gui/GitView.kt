package hamburg.remme.tinygit.gui

import hamburg.remme.tinygit.Settings
import hamburg.remme.tinygit.State
import hamburg.remme.tinygit.TinyGit
import hamburg.remme.tinygit.asPath
import hamburg.remme.tinygit.domain.Repository
import hamburg.remme.tinygit.exists
import hamburg.remme.tinygit.git.Git
import hamburg.remme.tinygit.git.PrepareSquashException
import hamburg.remme.tinygit.git.PullException
import hamburg.remme.tinygit.git.PushException
import hamburg.remme.tinygit.git.SquashException
import hamburg.remme.tinygit.git.TimeoutException
import hamburg.remme.tinygit.git.gitBranchList
import hamburg.remme.tinygit.git.gitFetchPrune
import hamburg.remme.tinygit.git.gitGc
import hamburg.remme.tinygit.git.gitHasRemote
import hamburg.remme.tinygit.git.gitHead
import hamburg.remme.tinygit.git.gitPull
import hamburg.remme.tinygit.git.gitPush
import hamburg.remme.tinygit.git.gitResetHard
import hamburg.remme.tinygit.git.gitStash
import hamburg.remme.tinygit.git.gitStashPop
import hamburg.remme.tinygit.gui.builder.Action
import hamburg.remme.tinygit.gui.builder.ActionCollection
import hamburg.remme.tinygit.gui.builder.ActionGroup
import hamburg.remme.tinygit.gui.builder.VBoxBuilder
import hamburg.remme.tinygit.gui.builder.addClass
import hamburg.remme.tinygit.gui.builder.choiceDialog
import hamburg.remme.tinygit.gui.builder.confirmWarningAlert
import hamburg.remme.tinygit.gui.builder.directoryChooser
import hamburg.remme.tinygit.gui.builder.errorAlert
import hamburg.remme.tinygit.gui.builder.flipX
import hamburg.remme.tinygit.gui.builder.flipXY
import hamburg.remme.tinygit.gui.builder.flipY
import hamburg.remme.tinygit.gui.builder.hbox
import hamburg.remme.tinygit.gui.builder.label
import hamburg.remme.tinygit.gui.builder.managedWhen
import hamburg.remme.tinygit.gui.builder.menuBar
import hamburg.remme.tinygit.gui.builder.progressSpinner
import hamburg.remme.tinygit.gui.builder.splitPane
import hamburg.remme.tinygit.gui.builder.stackPane
import hamburg.remme.tinygit.gui.builder.textAreaDialog
import hamburg.remme.tinygit.gui.builder.textInputDialog
import hamburg.remme.tinygit.gui.builder.toolBar
import hamburg.remme.tinygit.gui.builder.vgrow
import hamburg.remme.tinygit.gui.builder.visibleWhen
import hamburg.remme.tinygit.gui.component.Icons
import hamburg.remme.tinygit.gui.dialog.AboutDialog
import hamburg.remme.tinygit.gui.dialog.CloneDialog
import hamburg.remme.tinygit.gui.dialog.CommitDialog
import hamburg.remme.tinygit.gui.dialog.SettingsDialog
import javafx.application.Platform
import javafx.concurrent.Task
import javafx.scene.control.TabPane
import javafx.scene.layout.Priority
import javafx.scene.text.Text
import javafx.stage.Window
import org.eclipse.jgit.api.errors.JGitInternalException
import org.eclipse.jgit.api.errors.RefAlreadyExistsException
import org.eclipse.jgit.api.errors.StashApplyFailureException
import org.eclipse.jgit.api.errors.UnmergedPathsException

class GitView : VBoxBuilder() {

    private val window: Window get() = scene.window
    private val repositoryView = RepositoryView()
    private val commitLog = CommitLogView()
    private val workingCopy = WorkingCopyView()
    private val stats = StatsView()
    private val tabs = TabPane(commitLog, workingCopy, stats)

    init {
        addClass("git-view")

        // File
        val cloneRepo = Action("Clone Repository", { Icons.clone() }, "Shortcut+Shift+O",
                handler = { CloneDialog(window).show() })
        val newRepo = Action("New Repository", { Icons.folder() }, "Shortcut+N",
                handler = { newRepo() })
        val addRepo = Action("Add Repository", { Icons.folderOpen() }, "Shortcut+O",
                handler = { addRepo() })
        val quit = Action("Quit TinyGit", { Icons.signOut() },
                handler = { Platform.exit() })
        // View
        val showCommits = Action("Show Commits", { Icons.list() }, "F1", State.selectedRepository.isNull, // TODO: own prop?
                handler = { tabs.selectionModel.select(commitLog) })
        val showWorkingCopy = Action("Show Working Copy", { Icons.hdd() }, "F2", State.selectedRepository.isNull, // TODO: own prop?
                handler = { tabs.selectionModel.select(workingCopy) })
        val showStats = Action("Show Statistics", { Icons.chartPie() }, "F3", State.selectedRepository.isNull, // TODO: own prop?
                handler = { tabs.selectionModel.select(stats) })
        // TODO: add F5 refresh
        // Repository
        val commit = Action("Commit", { Icons.plus() }, "Shortcut+K", State.canCommit.not(),
                { CommitDialog(State.getSelectedRepository(), window).show() })
        val push = Action("Push", { Icons.cloudUpload() }, "Shortcut+P", State.canPush.not(),
                { push(State.getSelectedRepository(), false) }, State.ahead)
        val pushForce = Action("Force Push", { Icons.cloudUpload() }, "Shortcut+Shift+P", State.canForcePush.not(),
                { push(State.getSelectedRepository(), true) }, State.ahead)
        val pull = Action("Pull", { Icons.cloudDownload() }, "Shortcut+L", State.canPull.not(),
                { pull(State.getSelectedRepository()) }, State.behind)
        val fetch = Action("Fetch", { Icons.refresh() }, "Shortcut+F", State.canFetch.not(),
                { fetch(State.getSelectedRepository()) })
        val fetchGc = Action("GC", { Icons.eraser() }, "Shortcut+Shift+F", State.canGc.not(),
                { gc(State.getSelectedRepository()) })
        val branch = Action("Branch", { Icons.codeFork() }, "Shortcut+B", State.canBranch.not(),
                { createBranch(State.getSelectedRepository()) })
        val merge = Action("Merge", { Icons.codeFork().flipY() }, "Shortcut+M", State.canMerge.not(),
                handler = { merge(State.getSelectedRepository()) })
        val mergeContinue = Action("Continue Merge", { Icons.forward() }, "Shortcut+Shift+M", State.canMergeContinue.not(),
                handler = { CommitDialog(State.getSelectedRepository(), window).show() })
        val mergeAbort = Action("Abort Merge", { Icons.timesCircle() }, disable = State.canMergeAbort.not(),
                handler = { mergeAbort(State.getSelectedRepository()) })
        val rebase = Action("Rebase", { Icons.levelUp().flipX() }, "Shortcut+R", State.canRebase.not(),
                handler = { rebase(State.getSelectedRepository()) })
        val rebaseContinue = Action("Continue Rebase", { Icons.forward() }, "Shortcut+Shift+R", State.canRebaseContinue.not(),
                handler = { rebaseContinue(State.getSelectedRepository()) })
        val rebaseAbort = Action("Abort Rebase", { Icons.timesCircle() }, disable = State.canRebaseAbort.not(),
                handler = { rebaseAbort(State.getSelectedRepository()) })
        val stash = Action("Stash", { Icons.cube() }, "Shortcut+S", State.canStash.not(),
                { stash(State.getSelectedRepository()) })
        val stashPop = Action("Pop Stash", { Icons.cube().flipXY() }, "Shortcut+Shift+S", State.canApplyStash.not(),
                { stashPop(State.getSelectedRepository()) })
        val reset = Action("Auto-Reset", { Icons.undo() }, disable = State.canReset.not(),
                handler = { autoReset(State.getSelectedRepository()) })
        val squash = Action("Auto-Squash", { Icons.gavel() }, disable = State.canSquash.not(),
                handler = { autoSquash(State.getSelectedRepository()) }, count = State.aheadDefault)
        // ?
        val github = Action("Star TinyGit on GitHub", { Icons.github() },
                handler = { TinyGit.show("https://github.com/deso88/TinyGit") })
        val about = Action("About", { Icons.questionCircle() },
                handler = { AboutDialog(window).show() })

        +menuBar {
            isUseSystemMenuBar = true
            +ActionCollection("File", ActionGroup(cloneRepo, newRepo, addRepo), ActionGroup(quit))
            +ActionCollection("View", ActionGroup(showCommits, showWorkingCopy, showStats))
            +ActionCollection("Repository",
                    ActionGroup(push, pushForce, pull, fetch, fetchGc),
                    ActionGroup(branch, merge, mergeContinue, mergeAbort),
                    ActionGroup(rebase, rebaseContinue, rebaseAbort),
                    ActionGroup(reset, squash),
                    *repositoryView.actions)
            +ActionCollection("Actions",
                    ActionGroup(commit),
                    *workingCopy.actions,
                    ActionGroup(stash, stashPop))
            +ActionCollection("?", ActionGroup(github, about))
        }
        +toolBar {
            visibleWhen(State.showToolBar)
            managedWhen(State.showToolBar)
            +ActionGroup(addRepo)
            +ActionGroup(commit, push, pull, fetch)
            +ActionGroup(branch, merge)
            +ActionGroup(stash, stashPop)
            +ActionGroup(reset, squash)
        }
        +toolBar {
            visibleWhen(State.showMergeBar)
            managedWhen(State.showMergeBar)
            +ActionGroup(addRepo)
            +ActionGroup(mergeContinue, mergeAbort)
        }
        +toolBar {
            visibleWhen(State.showRebaseBar)
            managedWhen(State.showRebaseBar)
            +ActionGroup(addRepo)
            +ActionGroup(rebaseContinue, rebaseAbort)
        }
        +stackPane {
            vgrow(Priority.ALWAYS)
            +splitPane {
                addClass("content")
                +repositoryView
                +tabs
                Platform.runLater { setDividerPosition(0, 0.20) }
            }
            +stackPane {
                addClass("overlay")
                visibleWhen(State.showGlobalInfo)
                +hbox {
                    addClass("box")
                    +Text("Click ")
                    +Icons.folderOpen()
                    +Text(" to add a repository.")
                }
            }
            +stackPane {
                addClass("progress-overlay")
                visibleWhen(State.showGlobalOverlay)
                +progressSpinner {}
                +label { textProperty().bind(State.processTextProperty()) }
            }
        }

        Settings.setTabSelection { tabs.selectionModel.selectedIndex }
        Settings.load { tabs.selectionModel.select(it.tabSelection) }
    }

    private fun newRepo() {
        directoryChooser(window, "New Repository") {
            State.addRepository(Git.init(it.absolutePath))
        }
    }

    private fun addRepo() {
        directoryChooser(window, "Add Repository") {
            if ("${it.absolutePath}/.git".asPath().exists()) {
                State.addRepository(Repository(it.absolutePath))
            } else {
                errorAlert(window, "Invalid Repository",
                        "'${it.absolutePath}' does not contain a valid '.git' directory.")
            }
        }
    }

    private fun fetch(repository: Repository) {
        State.startProcess("Fetching...", object : Task<Unit>() {
            override fun call() = gitFetchPrune(repository)

            override fun succeeded() = State.fireRefresh(this)

            override fun failed() = exception.printStackTrace()
        })
    }

    private fun gc(repository: Repository) {
        State.startProcess("Cleaning up...", object : Task<Unit>() {
            override fun call() = gitGc(repository)

            override fun succeeded() = State.fireRefresh(this)

            override fun failed() = exception.printStackTrace()
        })
    }

    private fun pull(repository: Repository) {
        State.startProcess("Pulling commits...", object : Task<Unit>() {
            override fun call() = gitPull(repository)

            override fun succeeded() = State.fireRefresh(this)

            override fun failed() {
                when (exception) {
                    is PullException -> errorAlert(window, "Cannot Pull From Remote Branch", exception.message!!)
                    is TimeoutException -> errorAlert(window, "Connection Timed Out",
                            "Please check the repository settings.\nCredentials or proxy settings may have changed.")
                    else -> exception.printStackTrace()
                }
            }
        })
    }

    private fun push(repository: Repository, force: Boolean) {
        if (!gitHasRemote(repository)) {
            errorAlert(window, "Push", "No remote configured.")
            SettingsDialog(repository, window).show()
            return
        }

        if (force && !confirmWarningAlert(window, "Force Push", "Push",
                "This will rewrite the remote branch's history.\nChanges by others will be lost.")) return

        State.startProcess("Pushing commits...", object : Task<Unit>() {
            override fun call() = gitPush(repository, force)

            override fun succeeded() = State.fireRefresh(this)

            override fun failed() {
                when (exception) {
                    is PushException -> errorAlert(window, "Cannot Push to Remote Branch",
                            "Updates were rejected because the tip of the current branch is behind.\nPull before pushing again or force push.")
                    is TimeoutException -> errorAlert(window, "Connection Timed Out",
                            "Please check the repository settings.\nCredentials or proxy settings may have changed.")
                    else -> exception.printStackTrace()
                }
            }
        })
    }

    private fun createBranch(repository: Repository) {
        textInputDialog(window, "Enter a New Branch Name", "Create", Icons.codeFork()) { name ->
            State.startProcess("Branching...", object : Task<Unit>() {
                override fun call() = Git.branchCreate(repository, name)

                override fun succeeded() = State.fireRefresh(this)

                override fun failed() {
                    when (exception) {
                        is RefAlreadyExistsException -> errorAlert(window, "Cannot Create Branch",
                                "Branch '$name' does already exist in the working copy.")
                        is JGitInternalException -> errorAlert(window, "Cannot Create Branch",
                                "Invalid name '$name'.")
                        else -> exception.printStackTrace()
                    }
                }
            })
        }
    }

    private fun merge(repository: Repository) {
        val current = gitHead(repository)
        val branches = gitBranchList(repository).map { it.name }.filter { it != current }
        choiceDialog(window, "Select a Branch to Merge", "Merge", Icons.codeFork().flipY(), branches) { branch ->
            State.startProcess("Merging...", object : Task<Unit>() {
                override fun call() = Git.merge(repository, branch)

                override fun succeeded() = State.fireRefresh(this)

                override fun failed() = exception.printStackTrace()
            })
        }
    }

    private fun mergeAbort(repository: Repository) {
        State.startProcess("Aborting...", object : Task<Unit>() {
            override fun call() = Git.mergeAbort(repository)

            override fun succeeded() = State.fireRefresh(this)

            override fun failed() = exception.printStackTrace()
        })
    }

    private fun rebase(repository: Repository) {
        val current = gitHead(repository)
        val branches = gitBranchList(repository).map { it.name }.filter { it != current }
        choiceDialog(window, "Select a Branch for Rebasing", "Rebase", Icons.levelUp().flipX(), branches) { branch ->
            State.startProcess("Rebasing...", object : Task<Unit>() {
                override fun call() = Git.rebase(repository, branch)

                override fun succeeded() = State.fireRefresh(this)

                override fun failed() = exception.printStackTrace()
            })
        }
    }

    private fun rebaseContinue(repository: Repository) {
        State.startProcess("Rebasing...", object : Task<Unit>() {
            override fun call() = Git.rebaseContinue(repository)

            override fun succeeded() = State.fireRefresh(this)

            override fun failed() {
                when (exception) {
                    is UnmergedPathsException ->
                        errorAlert(window, "Unresolved Conflicts",
                                "Cannot continue with rebase because there are unresolved conflicts.")
                    else -> exception.printStackTrace()
                }
            }
        })
    }

    private fun rebaseAbort(repository: Repository) {
        State.startProcess("Aborting...", object : Task<Unit>() {
            override fun call() = Git.rebaseAbort(repository)

            override fun succeeded() = State.fireRefresh(this)

            override fun failed() = exception.printStackTrace()
        })
    }

    private fun stash(repository: Repository) {
        State.startProcess("Stashing files...", object : Task<Unit>() {
            override fun call() = gitStash(repository)

            override fun succeeded() = State.fireRefresh(this)

            override fun failed() = exception.printStackTrace()
        })
    }

    private fun stashPop(repository: Repository) {
        State.startProcess("Applying stash...", object : Task<Unit>() {
            override fun call() = gitStashPop(repository)

            override fun succeeded() = State.fireRefresh(this)

            override fun failed() {
                when (exception) {
                    is StashApplyFailureException -> {
                        State.fireRefresh(this)
                        errorAlert(window, "Cannot Pop Stash",
                                "Applying stashed changes resulted in a conflict.\nTherefore the stash entry has been preserved.")
                    }
                    else -> exception.printStackTrace()
                }
            }
        })
    }

    private fun autoReset(repository: Repository) {
        if (!confirmWarningAlert(window, "Auto Reset Branch", "Reset",
                "This will automatically reset the current branch to its remote branch.\nUnpushed commits will be lost.")) return

        State.startProcess("Resetting branch...", object : Task<Unit>() {
            override fun call() = gitResetHard(repository, gitHead(repository))

            override fun succeeded() = State.fireRefresh(this)

            override fun failed() = exception.printStackTrace()
        })
    }

    private fun autoSquash(repository: Repository) {
        val commits = Git.logWithoutDefault(repository)
        val message = commits.joinToString("\n\n") { "# ${it.shortId}\n${it.fullMessage}" }
        val baseId = commits.last().parents[0]
        val count = commits.size
        textAreaDialog(window, "Auto Squash Branch", "Squash", Icons.gavel(), message,
                "This will automatically squash all $count commits of the current branch.\n\nNew commit message:") {
            State.startProcess("Squashing branch...", object : Task<Unit>() {
                override fun call() = Git.rebaseSquash(repository, baseId, it)

                override fun succeeded() = State.fireRefresh(this)

                override fun failed() {
                    when (exception) {
                        is PrepareSquashException -> errorAlert(window, "Cannot Squash",
                                "${exception.message}\n\nPlease commit or stash them before squashing.")
                        is SquashException -> errorAlert(window, "Cannot Squash", exception.message!!)
                        else -> exception.printStackTrace()
                    }
                }
            })
        }
    }

}
