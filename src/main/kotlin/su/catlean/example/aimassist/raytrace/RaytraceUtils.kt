package su.catlean.example.aimassist.raytrace

import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import su.catlean.example.aimassist.data.Rotation
import su.catlean.example.aimassist.data.RotationWithVector
import su.catlean.example.aimassist.preference.LeastDifferencePreference
import su.catlean.example.player
import su.catlean.example.world

fun raytraceBox(
    eyes: Vec3,
    box: AABB,
    range: Double,
    wallsRange: Double,
    rotationPreference: LeastDifferencePreference
): RotationWithVector? {
    val rangeSquared = range * range
    val wallsRangeSquared = wallsRange * wallsRange

    val preferredSpot = rotationPreference.getPreferredSpotOnBox(box, eyes, range)
    // If the preferred point is already inside the hitbox, keep it.  Clipping
    // the segment in that case returns the entry face (often the top face when
    // the player is looking down), which creates a visible head/top-edge bias.
    val preferredSpotOnBox = if (box.contains(preferredSpot)) {
        preferredSpot
    } else {
        box.clip(eyes, preferredSpot).orElse(null)
    }

    if (preferredSpotOnBox != null) {
        val preferredSpotDistance = eyes.distanceToSqr(preferredSpotOnBox)
        val visible = hasLineOfSight(eyes, preferredSpotOnBox)

        if (isWithinAllowedRange(preferredSpotDistance, visible, rangeSquared, wallsRangeSquared)) {
            return RotationWithVector(
                Rotation.lookingAt(point = preferredSpotOnBox, from = eyes),
                preferredSpotOnBox
            )
        }
    }

    return raytraceBoxes(
        eyes = eyes,
        boxes = listOf(box),
        range = range,
        wallsRange = wallsRange,
        rotationPreference = rotationPreference
    )
}

fun raytraceBoxes(
    eyes: Vec3,
    boxes: Iterable<AABB>,
    range: Double,
    wallsRange: Double,
    rotationPreference: LeastDifferencePreference
): RotationWithVector? {
    val rangeSquared = range * range
    val wallsRangeSquared = wallsRange * wallsRange
    
    var bestVisible: RotationWithVector? = null
    var bestInvisible: RotationWithVector? = null

    for (box in boxes) {
        val preferredSpot = rotationPreference.getPreferredSpotOnBox(box, eyes, range)
        
        considerSpot(
            preferredSpot,
            box,
            eyes,
            rangeSquared,
            wallsRangeSquared,
            rotationPreference
        )?.let { (rotation, visible) ->
            if (visible) {
                val current = bestVisible
                if (current == null || rotationPreference.compare(current.rotation, rotation.rotation) > 0) {
                    bestVisible = rotation
                }
            } else {
                val current = bestInvisible
                if (current == null || rotationPreference.compare(current.rotation, rotation.rotation) > 0) {
                    bestInvisible = rotation
                }
            }
        }

        val nearestSpot = box.nearestPoint(eyes)
        considerSpot(
            nearestSpot,
            box,
            eyes,
            rangeSquared,
            wallsRangeSquared,
            rotationPreference
        )?.let { (rotation, visible) ->
            if (visible) {
                val current = bestVisible
                if (current == null || rotationPreference.compare(current.rotation, rotation.rotation) > 0) {
                    bestVisible = rotation
                }
            } else {
                val current = bestInvisible
                if (current == null || rotationPreference.compare(current.rotation, rotation.rotation) > 0) {
                    bestInvisible = rotation
                }
            }
        }

        scanBoxPoints(eyes, box) { spot ->
            considerSpot(
                spot,
                box,
                eyes,
                rangeSquared,
                wallsRangeSquared,
                rotationPreference
            )?.let { (rotation, visible) ->
                if (visible) {
                    val current = bestVisible
                    if (current == null || rotationPreference.compare(current.rotation, rotation.rotation) > 0) {
                        bestVisible = rotation
                    }
                } else {
                    val current = bestInvisible
                    if (current == null || rotationPreference.compare(current.rotation, rotation.rotation) > 0) {
                        bestInvisible = rotation
                    }
                }
            }
        }
    }

    return bestVisible ?: bestInvisible
}

private fun considerSpot(
    preferredSpot: Vec3,
    box: AABB,
    eyes: Vec3,
    rangeSquared: Double,
    wallsRangeSquared: Double,
    rotationPreference: LeastDifferencePreference
): Pair<RotationWithVector, Boolean>? {
    val raycastTarget = eyes.add(
        (preferredSpot.x - eyes.x) * 2.0,
        (preferredSpot.y - eyes.y) * 2.0,
        (preferredSpot.z - eyes.z) * 2.0
    )

    val spotOnBox = box.clip(eyes, raycastTarget).orElse(null) ?: return null
    val distSq = eyes.distanceToSqr(spotOnBox)

    val visible = hasLineOfSight(eyes, spotOnBox)

    if (!isWithinAllowedRange(distSq, visible, rangeSquared, wallsRangeSquared)) {
        return null
    }

    val rotation = Rotation.lookingAt(point = preferredSpot, from = eyes)
    return RotationWithVector(rotation, preferredSpot) to visible
}

private fun isWithinAllowedRange(
    distanceSquared: Double,
    visible: Boolean,
    rangeSquared: Double,
    wallsRangeSquared: Double
): Boolean = distanceSquared < (if (visible) rangeSquared else wallsRangeSquared)

fun hasLineOfSight(eyes: Vec3, target: Vec3): Boolean {
    return world.clip(
        ClipContext(
            eyes,
            target,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            player
        )
    ).type == HitResult.Type.MISS
}

fun isLookingAtEntity(
    fromEntity: Entity,
    toEntity: Entity,
    rotation: Rotation,
    range: Double,
    throughWallsRange: Double
): RotationWithVector? {
    val cameraVec = fromEntity.eyePosition
    val rotationVec = rotation.directionVector

    val vec3d3 = cameraVec.add(rotationVec.x * range, rotationVec.y * range, rotationVec.z * range)

    val targetBox = toEntity.boundingBox
    val clipResult = targetBox.clip(cameraVec, vec3d3).orElse(null) ?: return null
    
    val distance = cameraVec.distanceToSqr(clipResult)

    return if (distance <= throughWallsRange * throughWallsRange ||
        distance <= range * range && hasLineOfSight(cameraVec, clipResult)) {
        RotationWithVector(rotation, clipResult)
    } else null
}

private inline fun scanBoxPoints(eyes: Vec3, box: AABB, fn: (Vec3) -> Unit) {
    // Each point triggers a block raycast; avoid 10^3 raycasts per target per update.
    val proportions = doubleArrayOf(0.1, 0.3, 0.7, 0.9)
    
    for (x in proportions) {
        for (y in proportions) {
            for (z in proportions) {
                fn(box.pointAtProportion(x, y, z))
            }
        }
    }
}

private fun AABB.pointAtProportion(x: Double, y: Double, z: Double): Vec3 {
    return Vec3(
        minX + (maxX - minX) * x,
        minY + (maxY - minY) * y,
        minZ + (maxZ - minZ) * z
    )
}

private fun AABB.nearestPoint(pos: Vec3): Vec3 {
    val x = pos.x.coerceIn(minX, maxX)
    val y = pos.y.coerceIn(minY, maxY)
    val z = pos.z.coerceIn(minZ, maxZ)
    return Vec3(x, y, z)
}
