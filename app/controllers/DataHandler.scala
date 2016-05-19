package controllers

import scala.io.Source
// This fixes "reference must be prefixed" warnings
import scala.collection.mutable

import play.api.Play
import play.api.Play.current
import play.api.Logger

import JavaMI.Entropy
import JavaMI.MutualInformation


/**
  * Handles CSV reading
  */
object CsvReader {
  val log = Logger("CSVReader")

  /** String to Int, non-Ints get value "skipValue"
    *
    * This method is tuned to the known format of CSV data.
    * It is expected that only integers come from the CSV.
    * CSV is known to contain empty fields, those will be marked
    * "as invalid values" determined by the skipValue.
    *
    * @return Int
    */
  private def toIntWithSkip(s: String, skipValue: Int): Int = {
    try {
      s.toInt
    } catch {
      case e: Exception => skipValue
    }
  }

  /** Reads the default CSV file
    *
    * Read 1st row as list of variable names.
    * Drop first item of every row (the "id" item).
    * Mark every missing
    *
    * @return (List of variable names, Int[][] transpose of the data (with Skip flags))
    */
  def readCsvFromFile(csvPath: String, skipValue: Int) = {
    log.info(s"Reading CSV: $csvPath")
    val iter: Iterator[String] = Source.fromFile(Play.getFile(csvPath)).getLines

    // variable_names are on 1st row
    val names: Array[String] = iter.next().split(",").drop(1)

    // Read rest of csv
    val rows = mutable.ArrayBuffer[Array[Int]]()
    iter.foreach(rows += _.split(",", -1).drop(1).map(toIntWithSkip(_, skipValue)))
    (names, rows.toArray.transpose)
  }
}

/**
  * Main handler that calls CSV reading, calculation, and sends back
  * a Map of the salient values.
  *
  * Reads CSV data once and remembers it while the session lasts.
  */
object DataHandler {
  val log = Logger("DataHandler")
  val SKIP_FLAG = 1000  // marks item to be skipped in analysis
  private val DEFAULT_CSV = "conf/data/dataset.csv"

  // Might consider using this streaming method - might work in more cases
  /*
  lazy private val CSV_TEXT = scala.io.Source.fromInputStream(
    Play.resourceAsStream("public/data/dataset.csv") match { case Some(s) => s}
  )
  */

  // Use getAllFromCsv to access these. Stored after 1st read
  private var preReadNames: Option[Array[String]] = None
  private var preReadData: Option[Array[Array[Int]]] = None

  /** Main access for the CSV data
    *
    * Reads the default CSV from disk at first access.
    * On subsequent times it will offer the "preRead" information
    *
    * @return Tuple of (List of variable_names, Array of columnar arrays)
    */
  def getAllFromCsv = {
    if (preReadNames.isEmpty || preReadData.isEmpty) {
      log.info("Data not found, Reading from CSV")
      val (names, datas) = CsvReader.readCsvFromFile(DEFAULT_CSV, SKIP_FLAG)
      preReadNames = Some(names)
      preReadData = Some(datas)
    }
    // TODO: This unpacking just seems wrong...
    val retNames = preReadNames match {
      case Some(x) => x
    }
    val retData = preReadData match {
      case Some(x) => x
    }
    (retNames, retData)
  }

  /** Returns the variable names read from CSV */
  def getNames = {
    getAllFromCsv._1
  }

  /** Returns MI, d -values and VI for given variable_name
    *
    * Consider handling:
    * Does not protect against calling with wrong names.
    * Does not protect against wrong length arrays.
    *
    * @param inName The variable_name that exists in CSV
    * @return List of Maps( mi -> (d, variable_name) )
    */
  def getResults(inName: String) = {
    log.info(s"Getting MI for $inName")
    val (varNames, intData) = getAllFromCsv
    val base = varNames.indexOf(inName)

    // TODO: Structure of results is determined by clumsy JSON creation downstream
    val results = mutable.HashMap[Double, Tuple6[Double, Double, Double, Double, Double, String]]()

    // Run inName against every other variable column
    for (pred <- intData.indices.filter(_ != base)) {
      //log.info(s" against : ${varNames(pred)}")
      val (clean1, clean2) = prepArrays(intData(base), intData(pred))
      val mi = calcMI(clean1.map(_.toDouble), clean2.map(_.toDouble))
      val vi = calcVariationOfInformation(mi, clean1.map(_.toDouble), clean2.map(_.toDouble))
      val d_tt = dCalculator.calcD(clean1, clean2, 1, 1)
      val d_tf = dCalculator.calcD(clean1, clean2, 1, -1)
      val d_ft = dCalculator.calcD(clean1, clean2, -1, 1)
      val d_ff = dCalculator.calcD(clean1, clean2, -1, -1)
      results(mi) = (d_tt, d_tf, d_ft, d_ff, vi, varNames(pred))
    }
    results
  }

  /** Prepare two arrays for calculation
    *
    * - removes any pair which has SKIP_FLAG in either
    *
    * CSV data contained empty values, which were marked invalid upon
    * reading. We remove them here before calculations on a
    * pair-wise basis.
    * This causes us to loose some amount of information when calculating
    * p(A) or p(B), since missing item in one destroys the pair in the
    * other before calculations.
    *
    * @param unclean1 Array with possible invalid values
    * @param unclean2 Array with possible invalid values
    * @return Tuple2 of Int Arrays with only valid pairs of data
    */
  private def prepArrays(unclean1: Array[Int], unclean2: Array[Int]) = {
    val v1 = new mutable.ArrayBuffer[Int]()
    val v2 = new mutable.ArrayBuffer[Int]()
    for (i <- unclean1.indices) {
      if (unclean1(i) != SKIP_FLAG && unclean2(i) != SKIP_FLAG) {
        v1 += unclean1(i)
        v2 += unclean2(i)
      }
    }
    (v1.toArray, v2.toArray)
  }

  /** Calculate Mutual Information between 2 arrays
    *
    * MI quantifies the information that X and Y share:
    * it measures how much knowing one of these random variables
    * reduces uncertainty about the other.
    * Random variables are represented by the two input arrays.
    *
    * We are using Java-based JavaMI here for quick results.
    * Based on code review it
    * - seems to do I(X,Y) properly
    * - uses log_2
    * - uses 0*log(0/q) = 0 as default for missing cases between v1 and v2
    *
    * Other way to handle default value would be to give missing cases a default
    * probability of "if corpus was twice as large the case would appear once".
    *
    * Note: v1 and v2 are expected to be same length.
    *
    * @param v1 First array
    * @param v2 Second array
    * @return MI as a Double value
    */
  def calcMI(v1: Array[Double], v2: Array[Double]): Double = {
    val res = MutualInformation.calculateMutualInformation(v1, v2)
    res
  }

  /** Calculate Variation of Information between 2 arrays
    *
    * The variation of information is a metric.
    * VI(X; Y) measures how much knowing the cluster assignment for
    * an item in clustering X reduces the uncertainty about the
    * item's cluster in clustering Y.
    *
    * We are using the formula:
    * VI(X; Y) = H(X) + H(Y) = 2I(X; Y)
    *
    * For more see for ex.:
    * http://www.cs.umd.edu/class/spring2009/cmsc858l/InfoTheoryHints.pdf
    *
    * Note: v1 and v2 are expected to be same length.
    *
    * @param mi Mutual Information between the arrays
    * @param v1 First array
    * @param v2 Second array
    * @return VI as a Double value
    */
  def calcVariationOfInformation(mi: Double, v1: Array[Double], v2: Array[Double]): Double = {
    val res = Entropy.calculateEntropy(v1) + Entropy.calculateEntropy(v2) - 2 * mi
    res
  }
}

/**
  * Here we handle "d" calculation - read below for more.
  */
object dCalculator {
  private val log = Logger("dCalculator")

  /** Calculate d =  p(A&B) / p(A)p(B) 'tween 2 arrays
    *
    * This "d" gives a view into the direction connection between the
    * states of A and B. Where Mutual Information gives an indication
    * of how much you can infer of the state of B when you
    * only know the A. So MI gives a measure covering all the states,
    * but "d" is per-state-pair.
    * If the states would be "true" and "false", then "d" could be
    * calculated between say A(x=true) and B(y=true), or any other
    * pairing.
    *
    * Meaning:
    * If d=2 it means that the "state" x (in A) doubles the probability
    * of "state" y (in B). With d=0.5 y's probability would be halved.
    *
    * @param A Array of n kinds of "states". Must pair up with B's states.
    * @param B   -- " -- ... A's
    * @param x State from A s.t. p(A=x)  (and x is in A !!)
    * @param y State from B s.t. p(B=y)  (and y is in B !!)
    * @return Value of d for given pair of states
    */
  def calcD(A: Array[Int], B: Array[Int], x: Int, y: Int): Double = {

    val pA = mutable.HashMap[Int, Double]()
    val pB = mutable.HashMap[Int, Double]()
    val pAandB = mutable.HashMap[Tuple2[Int, Int], Double]()

    /**
      * Calculate pA, pB, and p(A&B) and store them for later use
      */
    def prepareCalcD(A: Array[Int], Bb: Array[Int]) = {
      // 1) get probabilities of states
      // 2) get probabilities of mutual states
      val len: Double = A.length
      val nA = mutable.HashMap[Int, Int]()
      val nB = mutable.HashMap[Int, Int]()
      val nAandB = mutable.HashMap[Tuple2[Int, Int], Int]()
      (A zip B) foreach { pair => {
        nA(pair._1) = nA.getOrElse(pair._1, 0) + 1
        nB(pair._2) = nB.getOrElse(pair._2, 0) + 1
        nAandB((pair._1, pair._2)) = nAandB.getOrElse((pair._1, pair._2), 0) + 1
      }}
      // Calculate and store the actual probabilities
      nA foreach ( (x) => pA(x._1) = x._2 / len )
      nB foreach ( (x) => pB(x._1) = x._2 / len )
      nAandB foreach ( (x) => pAandB(x._1) = x._2 / len )
    }

    prepareCalcD(A, B)
    // States that are missing completely should get some value.
    // If they are missing, their probability is most likely quite rare.
    // BUT exceptional values start to dominate the "d".
    // It explodes and becomes fantastical with default value Q:
    //  Q/(Q*0.08)  , or Q/(Q*Q)  <- those mean nothing.
    //
    // Hence we give will skip these cases and mark them with 0.0
    var d: Double = 0.0
    if ((A contains x) && (B contains y)) {
      // TODO: remove this default after unittests start working
      val default: Double = 1.0 / (2 * A.length)
      d = pAandB.getOrElse((x, y), default) /  (
        pA.getOrElse(x, default) * pB.getOrElse(y, default))
    }

    d
  }
}
