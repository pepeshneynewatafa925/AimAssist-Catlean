package su.catlean.example.aimassist.preference

import su.catlean.example.aimassist.data.Rotation
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class LeastDifferencePreference(
    private val baseRotation: Rotation,
    private val basePoint: Vec3? = null
) : Comparator<Rotation> {

    fun getPreferredSpot(eyesPos: Vec3, range: Double): Vec3 {
        if (basePoint != null) {
            return basePoint
        }
        val dir = baseRotation.directionVector
        return eyesPos.add(dir.x * range, dir.y * range, dir.z * range)
    }

    fun getPreferredSpotOnBox(box: AABB, eyesPos: Vec3, range: Double): Vec3 {
        if (basePoint != null) {
            return basePoint
        }

        val preferredSpot = getPreferredSpot(eyesPos, range)
        if (box.contains(preferredSpot)) {
            return preferredSpot
        }

        return box.clip(eyesPos, preferredSpot).orElse(preferredSpot)
    }

    override fun compare(o1: Rotation, o2: Rotation): Int {
        val rotationDifferenceO1 = baseRotation.rotationDeltaLengthTo(o1)
        val rotationDifferenceO2 = baseRotation.rotationDeltaLengthTo(o2)
        return rotationDifferenceO1.compareTo(rotationDifferenceO2)
    }

    companion object {
        fun leastDifferenceToLastPoint(eyes: Vec3, point: Vec3): LeastDifferencePreference {
            return LeastDifferencePreference(Rotation.lookingAt(point, from = eyes), point)
        }
    }
}
