package su.catlean.example.aimassist.utils

import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import su.catlean.example.aimassist.data.Rotation
import su.catlean.example.player
import kotlin.math.sqrt

object RotationUtil {

    private const val MOUSE_TURN_SCALE_FLOAT = 0.15f

    val gcd: Double
        get() {
            val sensitivity = Minecraft.getInstance().options.sensitivity().get()
            val f = sensitivity * 0.6f + 0.2f
            return (f * f * f * 8.0 * MOUSE_TURN_SCALE_FLOAT).toDouble()
        }

    fun mouseTurnDelta(cursorDeltaX: Double, cursorDeltaY: Double): Pair<Float, Float> {
        val deltaPitch = cursorDeltaY.toFloat() * MOUSE_TURN_SCALE_FLOAT
        val deltaYaw = cursorDeltaX.toFloat() * MOUSE_TURN_SCALE_FLOAT
        return Pair(deltaYaw, deltaPitch)
    }

    fun applyMouseTurnDelta(rotation: Rotation, cursorDeltaX: Double, cursorDeltaY: Double): Rotation {
        val (deltaYaw, deltaPitch) = mouseTurnDelta(cursorDeltaX, cursorDeltaY)
        return Rotation(
            yaw = rotation.yaw + deltaYaw,
            pitch = (rotation.pitch + deltaPitch).coerceIn(-90f, 90f)
        )
    }

    fun crosshairAngleToEntity(entity: Entity): Float {
        val player = player
        val eyes = player.eyePosition
        val rotationToEntity = Rotation.lookingAt(point = entity.boundingBox.center, from = eyes)
        return player.rotation.directionAngleTo(rotationToEntity)
    }

    fun angleDifference(a: Float, b: Float) = Mth.wrapDegrees(a - b)
}

val Player.rotation: Rotation
    get() = Rotation(yRot, xRot)

fun Player.setRotation(rotation: Rotation) {
    val normalized = rotation.normalize()
    xRotO = xRot
    yRotO = yRot
    yRot = normalized.yaw
    xRot = normalized.pitch
}
