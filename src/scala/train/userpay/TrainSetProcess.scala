package train.userpay

import mam.Dic
import mam.Utils.{getData, printDf, saveProcessedData}
import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql
import org.apache.spark.sql.functions._
import org.apache.spark.sql.{DataFrame, SparkSession}

import scala.collection.mutable.ArrayBuffer

object TrainSetProcess {

  def trainSetProcess(df_userProfilePlayPart: DataFrame, df_userProfilePreferencePart: DataFrame, df_userProfileOrderPart: DataFrame,
                      df_videoFirstCategory: DataFrame, df_videoSecondCategory: DataFrame, df_label: DataFrame, df_playVector: DataFrame, df_orderHistory: DataFrame,
                      trainUserProfileSavePath: String, trainSetSavePath: String) = {


    /**
     * Process User Profile Data
     */
    val joinKeysUserId = Seq(Dic.colUserId)
    val df_userPlayAndPref = df_userProfilePlayPart.join(df_userProfilePreferencePart, joinKeysUserId, "left")
    val df_userProfile = df_userPlayAndPref.join(df_userProfileOrderPart, joinKeysUserId, "left")


    val colList = df_userProfile.columns.toList
    val colTypeList = df_userProfile.dtypes.toList
    val mapColList = ArrayBuffer[String]()
    for (elem <- colTypeList) {
      if (!elem._2.equals("StringType") && !elem._2.equals("IntegerType")
        && !elem._2.equals("DoubleType") && !elem._2.equals("LongType")) {
        mapColList.append(elem._1)
      }
    }


    val numColList = colList.diff(mapColList)

    val df_userProfileFill = df_userProfile
      .na.fill(-1, List(Dic.colDaysSinceLastPurchasePackage, Dic.colDaysSinceLastClickPackage,
      Dic.colDaysFromLastActive, Dic.colDaysSinceFirstActiveInTimewindow))

    val df_userProfileFilled = df_userProfileFill.na.fill(0, numColList)


    var videoFirstCategoryMap: Map[String, Int] = Map()
    var videoSecondCategoryMap: Map[String, Int] = Map()
    var labelMap: Map[String, Int] = Map()


    // 一级分类

    var conList = df_videoFirstCategory.collect()
    for (elem <- conList) {
      val s = elem.toString()
      videoFirstCategoryMap += (s.substring(1, s.length - 1).split("\t")(1) -> s.substring(1, s.length - 1).split("\t")(0).toInt)

    }
    //二级分类
    conList = df_videoSecondCategory.collect()
    for (elem <- conList) {
      val s = elem.toString()
      videoSecondCategoryMap += (s.substring(1, s.length - 1).split("\t")(1) -> s.substring(1, s.length - 1).split("\t")(0).toInt)

    }
    //标签
    conList = df_label.collect()
    for (elem <- conList) {
      val s = elem.toString()
      labelMap += (s.substring(1, s.length - 1).split("\t")(1) -> s.substring(1, s.length - 1).split("\t")(0).toInt)

    }


    val pre = List(Dic.colVideoOneLevelPreference, Dic.colVideoTwoLevelPreference,
      Dic.colMovieTwoLevelPreference, Dic.colSingleTwoLevelPreference, Dic.colInPackageVideoTwoLevelPreference)

    var df_userProfileSplitPref = df_userProfileFilled

    def udfFillPreference = udf(fillPreference _)

    def fillPreference(prefer: Map[String, Int], offset: Int) = {
      if (prefer == null) {
        null
      } else {
        val mapArray = prefer.toArray
        if (mapArray.length > offset - 1) {
          mapArray(offset - 1)._1
        } else {
          null
        }
      }
    }

    for (elem <- pre) {
      df_userProfileSplitPref = df_userProfileSplitPref.withColumn(elem + "_1", udfFillPreference(col(elem), lit(1)))
        .withColumn(elem + "_2", udfFillPreference(col(elem), lit(2)))
        .withColumn(elem + "_3", udfFillPreference(col(elem), lit(3)))
    }


    def udfFillPreferenceIndex = udf(fillPreferenceIndex _)

    def fillPreferenceIndex(prefer: String, mapLine: String) = {
      if (prefer == null) {
        null
      } else {
        var tempMap: Map[String, Int] = Map()
        val lineIterator1 = mapLine.split(",")
        lineIterator1.foreach(m => tempMap += (m.split(" -> ")(0) -> m.split(" -> ")(1).toInt))
        tempMap.get(prefer)
      }
    }


    for (elem <- pre) {
      if (elem.contains(Dic.colVideoOneLevelPreference)) {
        df_userProfileSplitPref = df_userProfileSplitPref.withColumn(elem + "_1", udfFillPreferenceIndex(col(elem + "_1"), lit(videoFirstCategoryMap.mkString(","))))
          .withColumn(elem + "_2", udfFillPreferenceIndex(col(elem + "_2"), lit(videoFirstCategoryMap.mkString(","))))
          .withColumn(elem + "_3", udfFillPreferenceIndex(col(elem + "_3"), lit(videoFirstCategoryMap.mkString(","))))
      } else {
        df_userProfileSplitPref = df_userProfileSplitPref.withColumn(elem + "_1", udfFillPreferenceIndex(col(elem + "_1"), lit(videoSecondCategoryMap.mkString(","))))
          .withColumn(elem + "_2", udfFillPreferenceIndex(col(elem + "_2"), lit(videoSecondCategoryMap.mkString(","))))
          .withColumn(elem + "_3", udfFillPreferenceIndex(col(elem + "_3"), lit(videoSecondCategoryMap.mkString(","))))
      }
    }

    for (elem <- pre) {
      if (elem.equals(Dic.colVideoOneLevelPreference)) {
        df_userProfileSplitPref = df_userProfileSplitPref.na.fill(videoFirstCategoryMap.size, List(elem + "_1", elem + "_2", elem + "_3"))
      } else {
        df_userProfileSplitPref = df_userProfileSplitPref.na.fill(videoSecondCategoryMap.size, List(elem + "_1", elem + "_2", elem + "_3"))
      }
    }


    val columnTypeList = df_userProfileSplitPref.dtypes.toList
    val columnList = ArrayBuffer[String]()
    for (elem <- columnTypeList) {
      if (elem._2.equals("StringType") || elem._2.equals("IntegerType")
        || elem._2.equals("DoubleType") || elem._2.equals("LongType")) {
        columnList.append(elem._1)
      }
    }

    val df_trainUserProfile = df_userProfileSplitPref.select(columnList.map(df_userProfileSplitPref.col(_)): _*)

    // Save Train User Profile
    saveProcessedData(df_trainUserProfile, trainUserProfileSavePath)

    /**
     * Train User Profile Merge with Order History And Play History to be Train Set
     */

    val df_userProfileAndOrderHistory = df_trainUserProfile.join(df_orderHistory, joinKeysUserId, "left")
    val df_trainSet = df_userProfileAndOrderHistory.join(df_playVector, joinKeysUserId, "left")

    saveProcessedData(df_trainSet, trainSetSavePath)
    println("Done！")


  }


  def main(args: Array[String]): Unit = {
    System.setProperty("hadoop.home.dir", "c:\\winutils")
    Logger.getLogger("org").setLevel(Level.ERROR)

    val spark: SparkSession = new sql.SparkSession.Builder()
      .master("local[6]")
      .appName("TrainSetProcess")
      .getOrCreate()


    val time = args(0) + " " + args(1)
    println(time)

    //val hdfsPath = "hdfs:///pay_predict/"
    val hdfsPath = ""

    /**
     * Data Save Path
     */
    val trainUserProfileSavePath = hdfsPath + "data/train/userpay/trainUserProfile" + args(0)
    val trainSetSavePath = hdfsPath + "data/train/userpay/trainSet" + args(0)

    /**
     * User Profile Data Path
     */
    val userProfilePlayPartPath = hdfsPath + "data/train/common/processed/userpay/userprofileplaypart" + args(0)
    val userProfilePreferencePartPath = hdfsPath + "data/train/common/processed/userpay/userprofilepreferencepart" + args(0)
    val userProfileOrderPartPath = hdfsPath + "data/train/common/processed/userpay/userprofileorderpart" + args(0)

    /**
     * Medias Label Info Path
     */
    val videoFirstCategoryTempPath = hdfsPath + "data/train/common/processed/videofirstcategorytemp.txt"
    val videoSecondCategoryTempPath = hdfsPath + "data/train/common/processed/videosecondcategorytemp.txt"
    val labelTempPath = hdfsPath + "data/train/common/processed/labeltemp.txt"


    /**
     * User History Data Path
     */
    val userPlayVectorPath = hdfsPath + "data/train/common/processed/userpay/history/playHistoryVector" + args(0)
    val orderHistoryPath = hdfsPath + "data/train/common/processed/userpay/history/orderHistory" + args(0)


    /**
     * Get Data
     */
    val df_userProfilePlayPart = getData(spark, userProfilePlayPartPath)
    val df_userProfilePreferencePart = getData(spark, userProfilePreferencePartPath)
    val df_userProfileOrderPart = getData(spark, userProfileOrderPartPath)

    val df_videoFirstCategory = spark.read.format("csv").load(videoFirstCategoryTempPath)
    val df_videoSecondCategory = spark.read.format("csv").load(videoSecondCategoryTempPath)
    val df_label = spark.read.format("csv").load(labelTempPath)

    val df_playVector = getData(spark, userPlayVectorPath)
    val df_orderHistory = getData(spark, orderHistoryPath)


    trainSetProcess(df_userProfilePlayPart, df_userProfilePreferencePart, df_userProfileOrderPart,
      df_videoFirstCategory, df_videoSecondCategory, df_label, df_playVector, df_orderHistory,
      trainUserProfileSavePath, trainSetSavePath)


  }

}
