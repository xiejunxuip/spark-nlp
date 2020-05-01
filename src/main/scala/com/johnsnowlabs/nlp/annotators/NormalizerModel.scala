package com.johnsnowlabs.nlp.annotators

import com.johnsnowlabs.nlp.AnnotatorType.TOKEN
import com.johnsnowlabs.nlp.serialization.MapFeature
import com.johnsnowlabs.nlp.{Annotation, AnnotatorModel, ParamsAndFeaturesReadable}
import org.apache.spark.ml.param.{BooleanParam, StringArrayParam}
import org.apache.spark.ml.util.Identifiable

/**
  * Annotator that cleans out tokens. Requires stems, hence tokens.
  *
  * Removes all dirty characters from text following a regex pattern and transforms words based on a provided dictionary
  *
  * See [[https://github.com/JohnSnowLabs/spark-nlp/blob/master/src/test/scala/com/johnsnowlabs/nlp/annotators/NormalizerTestSpec.scala]] for examples on how to use the API
  *
  * @param uid required internal uid for saving annotator
  */
class NormalizerModel(override val uid: String) extends AnnotatorModel[NormalizerModel] {

  /** Output annotator type : TOKEN */
  override val outputAnnotatorType: AnnotatorType = TOKEN
  /** Input annotator type : TOKEN */
  override val inputAnnotatorTypes: Array[AnnotatorType] = Array(TOKEN)

  case class TokenizerAndNormalizerMap(beginTokenizer: Int, endTokenizer: Int, token: String,
                                       beginNormalizer: Int, endNormalizer: Int, normalizer: String)

  /** normalization regex patterns which match will be removed from token */
  val cleanupPatterns = new StringArrayParam(this, "cleanupPatterns", "normalization regex patterns which match will be removed from token")
  /** whether to convert strings to lowercase */
  val lowercase = new BooleanParam(this, "lowercase", "whether to convert strings to lowercase")
  /** slangDict */
  protected val slangDict: MapFeature[String, String] = new MapFeature(this, "slangDict")
  /** whether or not to be case sensitive to match slangs. Defaults to false. */
  val slangMatchCase = new BooleanParam(this, "slangMatchCase", "whether or not to be case sensitive to match slangs. Defaults to false.")

  def this() = this(Identifiable.randomUID("NORMALIZER"))

  /** Regular expressions list for normalization, defaults [^A-Za-z] */
  def setCleanupPatterns(value: Array[String]): this.type = set(cleanupPatterns, value)

  /** Regular expressions list for normalization, defaults [^A-Za-z] */
  def getCleanupPatterns: Array[String] = $(cleanupPatterns)

  /** Lowercase tokens, default true */
  def setLowercase(value: Boolean): this.type = set(lowercase, value)

  /** Lowercase tokens, default true */
  def getLowercase: Boolean = $(lowercase)


  /** Txt file with delimited words to be transformed into something else  */
  def setSlangDict(value: Map[String, String]): this.type = set(slangDict, value)

  /** Whether to convert string to lowercase or not while checking */
  def setSlangMatchCase(value: Boolean): this.type = set(slangMatchCase, value)

  /** Whether to convert string to lowercase or not while checking */
  def getSlangMatchCase: Boolean = $(slangMatchCase)

  def applyRegexPatterns(word: String): String = {

    val nToken = {
      get(cleanupPatterns).map(_.foldLeft(word)((currentText, compositeToken) => {
        currentText.replaceAll(compositeToken, "")
      })).getOrElse(word)
    }
    nToken
  }

  /** Txt file with delimited words to be transformed into something else  */
  protected def getSlangDict: Map[String, String] = $$(slangDict)

  /** ToDo: Review implementation, Current implementation generates spaces between non-words, potentially breaking tokens */
  override def annotate(annotations: Seq[Annotation]): Seq[Annotation] = {
    val normalizedAnnotations = annotations.flatMap { originalToken =>

      /** slang dictionary keys should have been lowercased if slangMatchCase is false */
      val unslanged = $$(slangDict).get(
        if ($(slangMatchCase)) originalToken.result
        else originalToken.result.toLowerCase
      )

      /** simple-tokenize the unslanged slag phrase */
      val tokenizedUnslang = {
        unslanged.map(unslang => {
          unslang.split(" ")
        }).getOrElse(Array(originalToken.result))
      }

      val cleaned = tokenizedUnslang.map(word => applyRegexPatterns(word))

      val cased = if ($(lowercase)) cleaned.map(_.toLowerCase) else cleaned

      cased.filter(_.nonEmpty).map { finalToken => {
        Annotation(
          outputAnnotatorType,
          originalToken.begin,
          originalToken.begin + finalToken.length - 1,
          finalToken,
          originalToken.metadata
        )
      }}

    }

    normalizedAnnotations

  }

}

object NormalizerModel extends ParamsAndFeaturesReadable[NormalizerModel]