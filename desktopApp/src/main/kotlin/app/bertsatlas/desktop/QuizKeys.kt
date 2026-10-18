package app.bertsatlas.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import app.bertsatlas.AppController
import app.bertsatlas.Destination
import app.bertsatlas.domain.model.Question
import app.bertsatlas.domain.model.RunPhase

object QuizKeys {
    private val choiceKeys = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six)

    fun handle(controller: AppController, event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown || event.isMetaPressed || event.isCtrlPressed || event.isAltPressed) return false
        val state = controller.state.value
        if (state.destination != Destination.Quiz || state.leaveConfirmationRequested) return false
        val run = state.run ?: return false
        return when (run.phase) {
            RunPhase.Feedback, RunPhase.FunFact -> if (event.key == Key.Enter || event.key == Key.Spacebar) {
                controller.continueRun()
                true
            } else false
            RunPhase.Answering -> answer(controller, run.activeQuestion, event)
            else -> false
        }
    }

    private fun answer(controller: AppController, active: app.bertsatlas.domain.model.ActiveQuestion, event: KeyEvent): Boolean =
        when (val question = active.question) {
            is Question.MultipleChoice -> {
                val index = choiceKeys.indexOf(event.key)
                val choice = active.orderedChoices.getOrNull(index)?.takeIf { it.id !in active.removedChoiceIds }
                if (choice != null) controller.answerChoice(choice.id)
                choice != null
            }
            is Question.LetterBank -> letterBank(controller, active, question, event)
            else -> false
        }

    private fun letterBank(controller: AppController, active: app.bertsatlas.domain.model.ActiveQuestion, question: Question.LetterBank, event: KeyEvent): Boolean {
        when (event.key) {
            Key.Enter -> {
                if (active.placements.size == question.displayAnswer.count { it.isLetter() }) controller.submitLetters()
                return true
            }
            Key.Backspace -> {
                active.placements.maxByOrNull { it.slotIndex }?.let { controller.removeLetter(it.tileId) }
                return true
            }
        }
        val typed = event.utf16CodePoint.takeIf { it > 0 }?.toChar() ?: return false
        if (!typed.isLetter()) return false
        val placed = active.placements.map { it.tileId }.toSet()
        val tile = active.letterTiles.firstOrNull { tile ->
            tile.id !in placed && tile.id !in active.removedTileIds && tile.character.equals(typed, ignoreCase = true)
        } ?: return true
        controller.placeLetter(tile.id)
        return true
    }
}
