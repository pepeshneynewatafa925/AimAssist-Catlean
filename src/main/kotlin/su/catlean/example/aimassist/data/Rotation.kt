package su.catlean.example.aimassist.data

import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Rotation(val yaw: Float, val pitch: Float) {

    val directionVector: Vec3
        get() {
            val yawRad = Math.toRadians(yaw.toDouble())
            val pitchRad = Math.toRadians(pitch.toDouble())
            val xz = cos(pitchRad)
            return Vec3(
                -sin(yawRad) * xz,
                -sin(pitchRad),
                cos(yawRad) * xz
            )
        }

    fun normalize(): Rotation {
        return Rotation(
            yaw = Mth.wrapDegrees(yaw),
            pitch = pitch.coerceIn(-90f, 90f)
        )
    }

    fun rotationDeltaTo(other: Rotation): Pair<Float, Float> {
        val yawDiff = Mth.wrapDegrees(other.yaw - yaw)
        val pitchDiff = other.pitch - pitch
        return Pair(yawDiff, pitchDiff)
    }

    fun rotationDeltaLengthTo(other: Rotation): Float {
        val (yawDiff, pitchDiff) = rotationDeltaTo(other)
        return sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff)
    }

    fun directionAngleTo(other: Rotation): Float {
        val (yawDiff, pitchDiff) = rotationDeltaTo(other)
        return sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff)
    }

    fun interpolateTo(target: Rotation, factor: Float): Rotation {
        val (yawDiff, pitchDiff) = rotationDeltaTo(target)
        return Rotation(
            yaw = yaw + yawDiff * factor,
            pitch = pitch + pitchDiff * factor
        )
    }

    companion object {
        fun lookingAt(point: Vec3, from: Vec3): Rotation {
            val diff = point.subtract(from)
            val dist = sqrt(diff.x * diff.x + diff.z * diff.z)
            val yaw = Math.toDegrees(atan2(diff.z, diff.x)).toFloat() - 90f
            val pitch = -Math.toDegrees(atan2(diff.y, dist)).toFloat()
            return Rotation(yaw, pitch)
        }
    }
}
