package com.novacut.editor.engine

import android.content.Context
import androidx.room.Room
import com.novacut.editor.engine.db.ProjectDatabase
import com.novacut.editor.engine.db.ProjectDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ProjectDatabase {
        return Room.databaseBuilder(
            context,
            ProjectDatabase::class.java,
            "novacut.db"
        )
            .addMigrations(*ProjectDatabase.ALL_MIGRATIONS)
            .build()
    }

    @Provides
    fun provideProjectDao(db: ProjectDatabase): ProjectDao = db.projectDao()
}
