package hamburg.remme.tinygit.domain.service

import hamburg.remme.tinygit.TinyGit
import hamburg.remme.tinygit.domain.Divergence
import hamburg.remme.tinygit.domain.Repository
import hamburg.remme.tinygit.git.gitDivergence
import hamburg.remme.tinygit.git.gitDivergenceExclusive
import javafx.beans.property.SimpleIntegerProperty
import javafx.concurrent.Task

class DivergenceService : Refreshable {

    val aheadDefault = SimpleIntegerProperty()
    val ahead = SimpleIntegerProperty()
    val behind = SimpleIntegerProperty()
    private var task: Task<*>? = null

    override fun onRefresh(repository: Repository) {
        update(repository)
    }

    override fun onRepositoryChanged(repository: Repository) {
        onRepositoryDeselected()
        update(repository)
    }

    override fun onRepositoryDeselected() {
        task?.cancel()
        aheadDefault.set(0)
        ahead.set(0)
        behind.set(0)
    }

    private fun update(repository: Repository) {
        task?.cancel()
        task = object : Task<Unit>() {
            private lateinit var divergence: Divergence
            private var divExclusive: Int = 0

            override fun call() {
                divExclusive = gitDivergenceExclusive(repository)
                divergence = gitDivergence(repository)
            }

            override fun succeeded() {
                aheadDefault.set(divExclusive)
                ahead.set(divergence.ahead)
                behind.set(divergence.behind)
            }
        }.also { TinyGit.execute(it) }
    }

}
