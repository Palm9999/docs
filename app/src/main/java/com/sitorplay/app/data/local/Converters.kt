package com.sitorplay.app.data.local

import androidx.room.TypeConverter
import com.sitorplay.app.domain.model.InjuryStatus
import com.sitorplay.app.domain.model.Position
import com.sitorplay.app.domain.model.RosterSlot

class Converters {
    @TypeConverter
    fun fromPosition(value: Position): String = value.name

    @TypeConverter
    fun toPosition(value: String): Position = Position.valueOf(value)

    @TypeConverter
    fun fromInjuryStatus(value: InjuryStatus): String = value.name

    @TypeConverter
    fun toInjuryStatus(value: String): InjuryStatus = InjuryStatus.valueOf(value)

    @TypeConverter
    fun fromRosterSlot(value: RosterSlot): String = value.name

    @TypeConverter
    fun toRosterSlot(value: String): RosterSlot = RosterSlot.valueOf(value)
}
