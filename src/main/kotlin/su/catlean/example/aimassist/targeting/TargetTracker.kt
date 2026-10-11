package su.catlean.example.aimassist.targeting

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.NeutralMob
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.player.Player
import su.catlean.example.aimassist.utils.RotationUtil
import su.catlean.example.player
import su.catlean.example.world

enum class TargetPriority {
    DISTANCE,
    HEALTH,
    DIRECTION
}

class TargetTracker(
    private val range: () -> Float,
    private val fov: () -> Float,
    private val onlyPlayers: () -> Boolean,
    private val includeInvisible: () -> Boolean,
    private val onlyArmor: () -> Boolean,
    private val onlyNetherite: () -> Boolean,
    private val priority: () -> TargetPriority
) {
    var target: LivingEntity? = null

    fun reset() {
        target = null
    }

    fun validate() {
        if (target != null && !isValid(target!!)) {
            target = null
        }
    }

    fun findTarget(): LivingEntity? {
        validate()

        target?.let { return it }

        val localPlayer = player
        val maxRangeSquared = range().toDouble().let { it * it }
        val maxAngle = fov() * 0.5f
        val selectedPriority = priority()
        var best: LivingEntity? = null
        var bestScore = Double.POSITIVE_INFINITY

        // Selecting the minimum in one pass avoids allocating and sorting a
        // candidate list on every player update.
        for (entity in world.entitiesForRendering()) {
            val living = entity as? LivingEntity ?: continue
            val score = scoreIfValid(
                living,
                localPlayer,
                maxRangeSquared,
                maxAngle,
                selectedPriority
            ) ?: continue
            if (score < bestScore) {
                bestScore = score
                best = living
            }
        }

        return best?.also { target = it }
    }

    private fun isValid(entity: LivingEntity): Boolean {
        return scoreIfValid(
            entity,
            player,
            range().toDouble().let { it * it },
            fov() * 0.5f,
            priority()
        ) != null
    }

    private fun scoreIfValid(
        entity: LivingEntity,
        localPlayer: LivingEntity,
        maxRangeSquared: Double,
        maxAngle: Float,
        selectedPriority: TargetPriority
    ): Double? {
        if (entity === localPlayer || entity.isRemoved || !entity.isAlive || entity.health <= 0f) return null
        if (entity is Player && (entity.isSpectator || entity.isCreative)) return null
        if (onlyPlayers() && entity !is Player) return null
        if (!includeInvisible() && entity.isInvisible) return null
        if (onlyArmor() && !hasArmor(entity)) return null
        if (onlyNetherite() && !hasNetherite(entity)) return null
        if (!entity.shouldBeAttacked()) return null

        val distance = squaredBoxDistance(entity)
        if (distance > maxRangeSquared || distance < 0.25) return null

        val angle = RotationUtil.crosshairAngleToEntity(entity)
        if (angle > maxAngle) return null

        return when (selectedPriority) {
            TargetPriority.DISTANCE -> distance
            TargetPriority.HEALTH -> entity.health.toDouble()
            TargetPriority.DIRECTION -> angle.toDouble()
        }
    }

    private fun squaredBoxDistance(entity: LivingEntity): Double {
        val box = entity.boundingBox
        val pos = player.eyePosition
        val x = pos.x - pos.x.coerceIn(box.minX, box.maxX)
        val y = pos.y - pos.y.coerceIn(box.minY, box.maxY)
        val z = pos.z - pos.z.coerceIn(box.minZ, box.maxZ)
        return x * x + y * y + z * z
    }

    private fun hasArmor(entity: LivingEntity): Boolean =
        ARMOR_SLOTS.any { !entity.getItemBySlot(it).isEmpty }

    private fun hasNetherite(entity: LivingEntity): Boolean =
        ARMOR_SLOTS.any { entity.getItemBySlot(it).item.toString().contains("netherite", ignoreCase = true) }

    private companion object {
        val ARMOR_SLOTS = listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)
    }
}

fun LivingEntity.shouldBeAttacked(): Boolean = when (this) {
    is Player -> !isSpectator && !isCreative
    is Enemy -> true
    is NeutralMob -> persistentAngerTarget == player.uuid
    else -> false
}
