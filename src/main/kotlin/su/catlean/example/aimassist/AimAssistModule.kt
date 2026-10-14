package su.catlean.example.aimassist

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.CrossbowItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.AxeItem
import net.minecraft.world.item.TridentItem
import net.minecraft.world.item.MaceItem
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import su.catlean.api.addon.feature.AddonModule
import su.catlean.api.event.events.player.AttackEvent
import su.catlean.api.event.events.player.PlayerUpdateEvent
import su.catlean.api.event.events.client.ScreenEvent
import su.catlean.example.aimassist.data.Rotation
import su.catlean.example.aimassist.data.RotationWithVector
import su.catlean.example.aimassist.preference.LeastDifferencePreference
import su.catlean.example.aimassist.raytrace.isLookingAtEntity
import su.catlean.example.aimassist.raytrace.raytraceBox
import su.catlean.example.aimassist.targeting.TargetPriority
import su.catlean.example.aimassist.targeting.TargetTracker
import su.catlean.example.aimassist.utils.RotationUtil
import su.catlean.example.aimassist.utils.rotation
import su.catlean.example.aimassist.utils.setRotation
import su.catlean.example.player
import su.catlean.example.world
import su.catlean.gofra.Flow
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

object AimAssistModule : AddonModule("Aim Assist", "legit", listOf("aa", "aimbot")) {
    private var priority by setting("Priority", TargetPriority.DIRECTION)
    private var range by setting("Range", 4.5f, 1f..20f)
    private var fov by setting("FOV", 360f, 20f..360f)
    private var holdAfterHit by setting("HoldAfterHit", 5f, 0f..30f)
    private var onlyLastHit by setting("OnlyLastHit", false)
    private var crossbowRange by setting("CrossbowRange", 20f, 1f..100f)

    private var onlyPlayers by setting("OnlyPlayers", false)
    private var includeInvisible by setting("Invisible", false)
    private var onlyArmor by setting("OnlyArmor", false)
    private var onlyNetherite by setting("OnlyNetherite", false)
    private var onlyWeapon by setting("OnlyWithWeapon", true)
    private var noInventory by setting("NoInventory", true)
    private var noEating by setting("NoEating", true)
    private var checkWalls by setting("CheckWalls", true)
    private var disableOnWorldChange by setting("DisableOnWorldChange", true)
    private var lazyRotation by setting("LazyRotation", false)

    // LegitAim settings.
    private var legitSpeed by setting("Legit-Speed", 1f, 0.1f..3f)
    private var legitSmoothing by setting("Legit-Smoothing", 1.2f, 0.2f..3f)
    private var legitMaxLerp by setting("Legit-MaxLerp", 1.5f, 0.2f..2.5f)
    private var legitMinLerp by setting("Legit-MinLerp", 0.3f, 0.01f..0.5f)
    private var legitRandomization by setting("Legit-Randomization", true)

    private val targetTracker = TargetTracker(
        { activeRange() }, { fov }, { onlyPlayers }, { includeInvisible },
        { onlyArmor }, { onlyNetherite }, { priority }
    )

    private var active = false
    private var currentTarget: LivingEntity? = null
    private var lastHitTarget: LivingEntity? = null
    private var lastHitTime = 0L
    private var worldIdentity: Any? = null
    private var targetRotation: Rotation? = null
    private var rawTargetRotation: Rotation? = null
    private var playerRotation: Rotation? = null
    private var legitPhase = 0
    private var legitPhaseStarted = 0L

    private fun activeRange(): Float = if (isChargedCrossbow()) crossbowRange else range

    override fun onEnable() {
        active = true
        resetState()
    }

    override fun onDisable() {
        active = false
        targetTracker.reset()
        resetState()
    }

    @Flow
    private fun onAttack(event: AttackEvent) {
        if (!active) return
        currentTarget?.let {
            lastHitTarget = it
            lastHitTime = System.currentTimeMillis()
        }
    }

    @Flow
    private fun onPlayerUpdate(event: PlayerUpdateEvent) {
        if (!active || Minecraft.getInstance().player == null || Minecraft.getInstance().level == null) return

        val level = world
        if (worldIdentity == null) worldIdentity = level.dimension()
        if (worldIdentity != level.dimension()) {
            if (disableOnWorldChange) {
                onDisable()
                return
            }
            resetState()
            worldIdentity = level.dimension()
        }

        if (!canAim()) {
            targetRotation = null
            rawTargetRotation = null
            return
        }

        val target = selectTarget()
        currentTarget = target
        playerRotation = player.rotation

        val result = target?.let(::calculateRotation)
        if (result == null) {
            targetRotation = null
            rawTargetRotation = null
            return
        }

        val current = playerRotation!!
        val smoothed = legitAim(current, result.rotation)
        rawTargetRotation = result.rotation
        targetRotation = smoothed
    }

    fun onMouseTurn(deltaX: Double, deltaY: Double): DoubleArray {
        if (!active) return doubleArrayOf(deltaX, deltaY)
        val target = rawTargetRotation ?: return doubleArrayOf(deltaX, deltaY)
        val current = playerRotation ?: player.rotation
        val before = current.rotationDeltaLengthTo(target)
        val after = RotationUtil.applyMouseTurnDelta(current, deltaX, deltaY)
        val afterError = after.rotationDeltaLengthTo(target)
        val movingAway = ((afterError - before) /
            after.rotationDeltaLengthTo(current).coerceAtLeast(0.001f)).coerceIn(0f, 1f)
        val proximity = (1f - before / 180f).coerceIn(0f, 1f)
        val scale = 1.0 - (0.3f * movingAway * proximity)
        val x = deltaX * scale
        val y = deltaY * scale
        playerRotation = playerRotation?.let { RotationUtil.applyMouseTurnDelta(it, x, y) }
        targetRotation = targetRotation?.let { RotationUtil.applyMouseTurnDelta(it, x, y) }
        return doubleArrayOf(x, y)
    }

    fun onWorldRender(partialTicks: Float) {
        if (!active) return
        val base = playerRotation ?: return
        val target = targetRotation ?: return
        player.setRotation(base.interpolateTo(target, partialTicks.coerceIn(0f, 1f)))
    }

    private fun selectTarget(): LivingEntity? {
        val now = System.currentTimeMillis()
        val holdMs = (holdAfterHit * 1000f).toLong()

        if (onlyLastHit) {
            return lastHitTarget?.takeIf { now - lastHitTime < max(holdMs, 1L) && isAllowed(it) }
        }

        val held = if (now - lastHitTime < holdMs) lastHitTarget else null
        if (held != null && isAllowed(held)) return held
        return targetTracker.findTarget()
    }

    private fun isAllowed(entity: LivingEntity): Boolean {
        if (entity.isRemoved || !entity.isAlive || entity.health <= 0f) return false
        if (entity.level() !== world) return false
        val maxRange = activeRange().toDouble()
        if (player.distanceToSqr(entity) > maxRange * maxRange) return false
        return true
    }

    private fun calculateRotation(entity: LivingEntity): RotationWithVector? {
        val eyes = player.eyePosition
        val box = entity.boundingBox.inflate(entity.pickRadius.toDouble())
        val effectiveRange = activeRange().toDouble()
        if (lazyRotation && isLookingAtEntity(
                fromEntity = player,
                toEntity = entity,
                rotation = player.rotation,
                range = effectiveRange,
                throughWallsRange = 0.0
            ) == null
        ) return null

        val adaptivePoint = adaptiveAimPoint(eyes, box)
        return raytraceBox(
            eyes = eyes,
            box = box,
            range = effectiveRange,
            wallsRange = if (checkWalls) 0.0 else effectiveRange,
            rotationPreference = LeastDifferencePreference.leastDifferenceToLastPoint(eyes, adaptivePoint)
        )
    }

    /**
     * Picks a stable point inside the hitbox instead of always aiming at its
     * geometric centre.  The height follows the old client's distance-based
     * rule and is corrected for the vertical angle to the target.
     */
    private fun adaptiveAimPoint(eyes: Vec3, box: AABB): Vec3 {
        val centerX = (box.minX + box.maxX) * 0.5
        val centerZ = (box.minZ + box.maxZ) * 0.5
        val horizontalDistance = sqrt(
            (centerX - eyes.x) * (centerX - eyes.x) +
                (centerZ - eyes.z) * (centerZ - eyes.z)
        )
        val height = box.maxY - box.minY

        var heightFactor = when {
            horizontalDistance <= 4.0 -> 0.55
            horizontalDistance <= 12.0 -> {
                val t = (horizontalDistance - 4.0) / 8.0
                0.55 + t * 0.30
            }
            else -> {
                val t = ((horizontalDistance - 12.0) / 13.0).coerceIn(0.0, 1.0)
                0.85 + t * 0.20
            }
        }

        val verticalDistance = box.minY + height * 0.5 - eyes.y
        if (horizontalDistance > 0.5) {
            heightFactor -= (verticalDistance / horizontalDistance * 0.15).coerceIn(-0.15, 0.15)
        }

        // Keep the preferred point inside the box; above-max values make the
        // raycast snap back to the top face instead of aiming inside it.
        heightFactor = heightFactor.coerceIn(0.2, 0.95)
        return Vec3(centerX, box.minY + height * heightFactor, centerZ)
    }

    private fun legitAim(current: Rotation, target: Rotation): Rotation {
        val (yaw, pitch) = current.rotationDeltaTo(target)
        val distance = sqrt(yaw * yaw + pitch * pitch)
        if (System.currentTimeMillis() - legitPhaseStarted > 3000L) {
            legitPhase = (legitPhase + 1) % 5
            legitPhaseStarted = System.currentTimeMillis()
        }
        val randomFactor = if (!legitRandomization) 1f else when (legitPhase) {
            0 -> Random.nextDouble(0.6, 1.0)
            1 -> Random.nextDouble(0.8, 1.1)
            2 -> Random.nextDouble(0.5, 0.8)
            3 -> Random.nextDouble(0.7, 1.2)
            else -> Random.nextDouble(0.4, 0.8)
        }
        var lerp = (legitSpeed.toDouble() / 3.0) * randomFactor.toDouble() / legitSmoothing.toDouble()
        lerp *= if (distance < 15f) 0.7 else if (distance > 120f) 1.3 else 1.0
        lerp = lerp.coerceIn(legitMinLerp.toDouble(), legitMaxLerp.toDouble())
        // LEGIT is yaw-only by design: pitch is left to the player.
        return Rotation(current.yaw + yaw * lerp.toFloat(), current.pitch)
    }

    private fun canAim(): Boolean {
        if (onlyWeapon && !isWeapon(player.mainHandItem) && !isChargedCrossbow()) return false
        if (noInventory && ScreenEvent.screen != null) return false
        if (noEating && player.isUsingItem) return false
        return true
    }

    private fun isWeapon(stack: ItemStack): Boolean {
        val item = stack.item
        return item.toString().contains("sword", ignoreCase = true) ||
            item is AxeItem || item is TridentItem || item is MaceItem
    }

    private fun isChargedCrossbow(): Boolean =
        CrossbowItem.isCharged(player.mainHandItem) || CrossbowItem.isCharged(player.offhandItem)

    private fun resetState() {
        currentTarget = null
        lastHitTarget = null
        lastHitTime = 0L
        worldIdentity = null
        targetRotation = null
        rawTargetRotation = null
        playerRotation = null
        legitPhase = 0
        legitPhaseStarted = System.currentTimeMillis()
    }
}
