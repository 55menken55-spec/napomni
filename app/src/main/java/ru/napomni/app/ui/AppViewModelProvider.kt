package ru.napomni.app.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ru.napomni.app.NapomniApp
import ru.napomni.app.ui.categories.CategoriesViewModel
import ru.napomni.app.ui.notes.NoteEditViewModel
import ru.napomni.app.ui.notes.NotesViewModel
import ru.napomni.app.ui.reminders.ReminderEditViewModel
import ru.napomni.app.ui.reminders.RemindersViewModel
import ru.napomni.app.ui.settings.SettingsViewModel
import ru.napomni.app.ui.stats.StatsViewModel

/** Фабрика ViewModel-ей (простая DI без Hilt — ТЗ, раздел 2). */
object AppViewModelProvider {

    val Factory = viewModelFactory {
        initializer {
            RemindersViewModel(app().container.reminderRepository, app().container.alarmScheduler)
        }
        initializer {
            ReminderEditViewModel(
                app().container.reminderRepository,
                app().container.alarmScheduler,
                createSavedStateHandle(),
            )
        }
        initializer { NotesViewModel(app().container.noteRepository) }
        initializer {
            NoteEditViewModel(app().container.noteRepository, createSavedStateHandle())
        }
        initializer {
            SettingsViewModel(
                app().container.settingsRepository,
                app().container.backupManager,
                app().container.notificationHelper
            )
        }
        initializer { CategoriesViewModel(app().container.categoryRepository) }
        initializer {
            StatsViewModel(
                app().container.reminderRepository,
                app().container.occurrenceRepository,
            )
        }
    }

    private fun CreationExtras.app(): NapomniApp =
        this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NapomniApp
}
