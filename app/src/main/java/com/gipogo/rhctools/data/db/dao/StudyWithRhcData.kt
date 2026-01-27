package com.gipogo.rhctools.data.db.dao

import androidx.room.Embedded
import androidx.room.Relation
import com.gipogo.rhctools.data.db.entities.RhcStudyDataEntity
import com.gipogo.rhctools.data.db.entities.StudyEntity

data class StudyWithRhcData(
    @Embedded val study: StudyEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "studyId"
    )
    val rhc: RhcStudyDataEntity?
)
