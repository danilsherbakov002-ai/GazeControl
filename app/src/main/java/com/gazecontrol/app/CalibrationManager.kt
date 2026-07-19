package com.gazecontrol.app

import android.content.Context
import android.content.SharedPreferences

data class CalibrationPoint(
    val gazeX: Float,
    val gazeY: Float,
    val screenX: Float,
    val screenY: Float
)

/** Коэффициенты аффинного преобразования: screen = a*gazeX + b*gazeY + c */
data class AffineCoeffs(val a: Float, val b: Float, val c: Float)

data class GazeMapping(val forX: AffineCoeffs, val forY: AffineCoeffs) {
    fun map(gazeX: Float, gazeY: Float): Pair<Float, Float> {
        val sx = forX.a * gazeX + forX.b * gazeY + forX.c
        val sy = forY.a * gazeX + forY.b * gazeY + forY.c
        return sx to sy
    }
}

object CalibrationManager {
    private const val PREFS = "gaze_calibration"
    private const val KEY_COEFFS = "coeffs"

    /** Решает систему методом наименьших квадратов (нормальные уравнения, 3x3). */
    private fun leastSquares3(points: List<CalibrationPoint>, target: (CalibrationPoint) -> Float): AffineCoeffs {
        // Модель: target = a*gx + b*gy + c
        // Строим A^T A и A^T y для [gx, gy, 1]
        var s_xx = 0.0; var s_xy = 0.0; var s_x = 0.0
        var s_yy = 0.0; var s_y = 0.0
        var n = 0.0
        var s_xt = 0.0; var s_yt = 0.0; var s_t = 0.0

        for (p in points) {
            val gx = p.gazeX.toDouble()
            val gy = p.gazeY.toDouble()
            val t = target(p).toDouble()
            s_xx += gx * gx; s_xy += gx * gy; s_x += gx
            s_yy += gy * gy; s_y += gy
            n += 1.0
            s_xt += gx * t; s_yt += gy * t; s_t += t
        }

        // Матрица M * [a,b,c]^T = v
        val m = arrayOf(
            doubleArrayOf(s_xx, s_xy, s_x),
            doubleArrayOf(s_xy, s_yy, s_y),
            doubleArrayOf(s_x, s_y, n)
        )
        val v = doubleArrayOf(s_xt, s_yt, s_t)

        val sol = solve3x3(m, v) ?: doubleArrayOf(0.0, 0.0, points.map { target(it) }.average())
        return AffineCoeffs(sol[0].toFloat(), sol[1].toFloat(), sol[2].toFloat())
    }

    /** Гауссово исключение для системы 3x3. */
    private fun solve3x3(mIn: Array<DoubleArray>, vIn: DoubleArray): DoubleArray? {
        val m = Array(3) { mIn[it].copyOf() }
        val v = vIn.copyOf()

        for (col in 0..2) {
            var pivotRow = col
            for (r in col + 1..2) {
                if (kotlin.math.abs(m[r][col]) > kotlin.math.abs(m[pivotRow][col])) pivotRow = r
            }
            if (kotlin.math.abs(m[pivotRow][col]) < 1e-9) return null
            val tmpRow = m[col]; m[col] = m[pivotRow]; m[pivotRow] = tmpRow
            val tmpV = v[col]; v[col] = v[pivotRow]; v[pivotRow] = tmpV

            val pivot = m[col][col]
            for (c in 0..2) m[col][c] = m[col][c] / pivot
            v[col] = v[col] / pivot

            for (r in 0..2) {
                if (r == col) continue
                val factor = m[r][col]
                for (c in 0..2) m[r][c] -= factor * m[col][c]
                v[r] -= factor * v[col]
            }
        }
        return v
    }

    fun computeMapping(points: List<CalibrationPoint>): GazeMapping {
        val forX = leastSquares3(points) { it.screenX }
        val forY = leastSquares3(points) { it.screenY }
        return GazeMapping(forX, forY)
    }

    fun save(context: Context, mapping: GazeMapping) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val serialized = listOf(
            mapping.forX.a, mapping.forX.b, mapping.forX.c,
            mapping.forY.a, mapping.forY.b, mapping.forY.c
        ).joinToString(",")
        prefs.edit().putString(KEY_COEFFS, serialized).apply()
    }

    fun load(context: Context): GazeMapping? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_COEFFS, null) ?: return null
        val parts = raw.split(",").map { it.toFloatOrNull() }
        if (parts.size != 6 || parts.any { it == null }) return null
        return GazeMapping(
            AffineCoeffs(parts[0]!!, parts[1]!!, parts[2]!!),
            AffineCoeffs(parts[3]!!, parts[4]!!, parts[5]!!)
        )
    }

    fun hasCalibration(context: Context): Boolean = load(context) != null
}
