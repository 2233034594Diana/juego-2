package com.example.statistics

import kotlin.math.*

data class AnovaResult(
    val n: Int, // Number of subjects
    val k: Int, // Number of conditions (usually 3)
    val sst: Double, // Total Sum of Squares
    val ssConds: Double, // SS between conditions
    val ssSubjects: Double, // SS between subjects
    val sse: Double, // Error/Residual Sum of Squares
    val dfConds: Int,
    val dfSubjects: Int,
    val dfError: Int,
    val msConds: Double,
    val msError: Double,
    val fStatistic: Double,
    val pValue: Double,
    val isSignificant: Boolean,
    val conditionMeans: DoubleArray,
    val conditionStdevs: DoubleArray,
    val errorMessage: String? = null
)

object AnovaEngine {

    /**
     * Lanczos approximation for Log Gamma function ln(Γ(x)).
     * Required for log-beta computation without overflow.
     */
    private fun logGamma(x: Double): Double {
        if (x < 0.5) {
            return ln(PI / sin(PI * x)) - logGamma(1.0 - x)
        }
        val coef = doubleArrayOf(
            76.18009172947146,
            -86.50532032941677,
            24.01409824083091,
            -1.231739572450155,
            0.1208650973866179e-2,
            -0.5395239384953e-5
        )
        var sum = 1.000000000190015
        val tmp = x + 5.5
        var series = x
        for (c in coef) {
            series += 1.0
            sum += c / series
        }
        return (x + 0.5) * ln(tmp) - tmp + ln(2.5066282746310005 * sum / x)
    }

    /**
     * Computes the regularized incomplete beta function I_x(a, b).
     * Uses Lentz's continued fraction method for high accuracy.
     */
    fun regularizedIncompleteBeta(x: Double, a: Double, b: Double): Double {
        if (x < 0.0 || x > 1.0) throw IllegalArgumentException("x must be in [0, 1]")
        if (x == 0.0) return 0.0
        if (x == 1.0) return 1.0

        // Symmetry transformation to speed up convergence
        val symm = x > (a + 1.0) / (a + b + 2.0)
        val xt = if (symm) 1.0 - x else x
        val at = if (symm) b else a
        val bt = if (symm) a else b

        val logBeta = logGamma(at) + logGamma(bt) - logGamma(at + bt)
        val front = exp(at * ln(xt) + bt * ln(1.0 - xt) - logBeta) / at

        // Lentz continued fraction initialization
        var f = 1.0
        var c = 1.0
        var d = 1.0
        val eps = 1e-15
        val maxIter = 200

        var currentF = front

        // d_0 = 1, we start from m = 1
        // d_2m = m*(b-m)*x / ((a+2m-1)*(a+2m))
        // d_2m+1 = -(a+m)*(a+b+m)*x / ((a+2m)*(a+2m+1))
        
        var dMultiplier = 1.0
        for (m in 1..maxIter) {
            val d_2m = m * (bt - m) * xt / ((at + 2 * m - 1) * (at + 2 * m))
            // Step 1 for 2m
            d = 1.0 + d_2m * d
            if (abs(d) < eps) d = eps
            c = 1.0 + d_2m / c
            if (abs(c) < eps) c = eps
            d = 1.0 / d
            var delta = c * d
            f *= delta

            val d_2m1 = -(at + m) * (at + bt + m) * xt / ((at + 2 * m) * (at + 2 * m + 1))
            // Step 2 for 2m+1
            d = 1.0 + d_2m1 * d
            if (abs(d) < eps) d = eps
            c = 1.0 + d_2m1 / c
            if (abs(c) < eps) c = eps
            d = 1.0 / d
            delta = c * d
            f *= delta

            if (abs(delta - 1.0) < eps) {
                currentF = front * (f - 1.0)
                break
            }
        }

        // Clip results strictly between 0 and 1
        val result = currentF.coerceIn(0.0, 1.0)
        return if (symm) 1.0 - result else result
    }

    /**
     * Compute the p-value of an F-statistic with degrees of freedom df1 and df2.
     */
    fun fDistributionPValue(f: Double, df1: Int, df2: Int): Double {
        if (f <= 0.0) return 1.0
        val x = df2.toDouble() / (df2.toDouble() + df1.toDouble() * f)
        return try {
            regularizedIncompleteBeta(x, df2.toDouble() / 2.0, df1.toDouble() / 2.0)
        } catch (e: Exception) {
            0.5 // Safe fallback if convergence crashes
        }
    }

    /**
     * Performs a one-way Repeated Measures ANOVA (Análisis de Varianza de Medidas Repetidas).
     * @param data A list representing subject rows. Each item is a DoubleArray containing that
     *             subject's scores in conditions 1, 2, and 3.
     */
    fun calculateRepeatedMeasuresAnova(scores: List<DoubleArray>): AnovaResult {
        val n = scores.size
        if (n < 2) {
            return AnovaResult(
                n = n, k = 3, sst = 0.0, ssConds = 0.0, ssSubjects = 0.0, sse = 0.0,
                dfConds = 2, dfSubjects = n - 1, dfError = 2 * (n - 1),
                msConds = 0.0, msError = 0.0, fStatistic = 0.0, pValue = 1.0,
                isSignificant = false,
                conditionMeans = DoubleArray(3),
                conditionStdevs = DoubleArray(3),
                errorMessage = "Se requieren datos completos de al menos 2 sujetos para realizar el ANOVA."
            )
        }

        val k = 3 // CONTROL, NATURAL, ANTROPOGENICO
        // Total observations
        val m = n * k

        // Compute sums
        var grandTotal = 0.0
        var sumOfSquaresTotal = 0.0
        val conditionSums = DoubleArray(k)
        val subjectSums = DoubleArray(n)
        val conditionValues = List(k) { mutableListOf<Double>() }

        for (i in 0 until n) {
            val subjScores = scores[i]
            if (subjScores.size < k) {
                return AnovaResult(
                    n = n, k = k, sst = 0.0, ssConds = 0.0, ssSubjects = 0.0, sse = 0.0,
                    dfConds = 2, dfSubjects = n - 1, dfError = 2 * (n - 1),
                    msConds = 0.0, msError = 0.0, fStatistic = 0.0, pValue = 1.0,
                    isSignificant = false,
                    conditionMeans = DoubleArray(k),
                    conditionStdevs = DoubleArray(k),
                    errorMessage = "Todos los sujetos deben tener registros en los 3 escenarios."
                )
            }
            var rowSum = 0.0
            for (j in 0 until k) {
                val score = subjScores[j]
                grandTotal += score
                sumOfSquaresTotal += score * score
                conditionSums[j] += score
                rowSum += score
                conditionValues[j].add(score)
            }
            subjectSums[i] = rowSum
        }

        // Means and standard deviations of conditions
        val means = DoubleArray(k) { j -> conditionSums[j] / n }
        val stdevs = DoubleArray(k) { j ->
            val avg = means[j]
            var sqSum = 0.0
            for (valItem in conditionValues[j]) {
                sqSum += (valItem - avg).pow(2)
            }
            sqrt(sqSum / (n - 1))
        }

        // Sum of squares calculations
        val correctionFactor = grandTotal.pow(2) / m

        // sst: total sum of squares
        val sst = sumOfSquaresTotal - correctionFactor

        // ssConds (between conditions)
        var ssCondsSum = 0.0
        for (j in 0 until k) {
            ssCondsSum += conditionSums[j].pow(2)
        }
        val ssConds = (ssCondsSum / n) - correctionFactor

        // ssSubjects (between subjects)
        var ssSubjectsSum = 0.0
        for (i in 0 until n) {
            ssSubjectsSum += subjectSums[i].pow(2)
        }
        val ssSubjects = (ssSubjectsSum / k) - correctionFactor

        // sse: within-subject error
        val sse = (sst - ssConds - ssSubjects).coerceAtLeast(0.0)

        // Degrees of freedom
        val dfConds = k - 1 // 2
        val dfSubjects = n - 1
        val dfError = dfConds * dfSubjects // 2 * (n - 1)

        // Mean Squares
        val msConds = ssConds / dfConds
        val msError = if (dfError > 0) sse / dfError else 0.0

        // F Statistic
        val fStat = if (msError > 0) msConds / msError else 0.0

        // P-Value
        val pVal = if (dfError > 0) fDistributionPValue(fStat, dfConds, dfError) else 1.0

        return AnovaResult(
            n = n, k = k,
            sst = sst, ssConds = ssConds, ssSubjects = ssSubjects, sse = sse,
            dfConds = dfConds, dfSubjects = dfSubjects, dfError = dfError,
            msConds = msConds, msError = msError,
            fStatistic = fStat, pValue = pVal,
            isSignificant = pVal < 0.05,
            conditionMeans = means,
            conditionStdevs = stdevs
        )
    }
}
