package predict.common

import mam.Dic
import mam.Utils.{calDate, udfGetDays}
import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql
import org.apache.spark.sql.{SaveMode, SparkSession}

import scala.collection.mutable.ListBuffer

object UserProfileGeneratePlayPart {

  def userProfileGeneratePlayPart(now:String,timeWindow:Int,medias_path:String,plays_path:String,orders_path:String,hdfsPath:String): Unit = {
    System.setProperty("hadoop.home.dir", "c:\\winutils")
    Logger.getLogger("org").setLevel(Level.ERROR)
    val spark: SparkSession = new sql.SparkSession.Builder()
      .appName("UserProfileGeneratePlayPart")
      .master("local[6]")
      .getOrCreate()
    //设置shuffle过程中分区数
   // spark.sqlContext.setConf("spark.sql.shuffle.partitions", "1000")
    import org.apache.spark.sql.functions._

    val medias = spark.read.format("parquet").load(medias_path)
    val plays = spark.read.format("parquet").load(plays_path)
    val orders = spark.read.format("parquet").load(orders_path)


    val result=plays.select(col(Dic.colUserId)).distinct()

    //val result = users.toDF("user_id")

    val pre_30 = calDate(now, -30)
    val pre_14 = calDate(now, days = -14)
    val pre_7 = calDate(now, -7)
    val pre_3 = calDate(now, -3)
    val pre_1 = calDate(now, -1)

    //设置DataFrame列表
    val dataFrameList=ListBuffer()


    val play_part_1 = plays
      .filter(
        col(Dic.colPlayEndTime).<(now)
          && col(Dic.colPlayEndTime).>=(pre_30))
      .groupBy(col(Dic.colUserId))
      .agg(
        countDistinct(col(Dic.colPlayEndTime)).as(Dic.colActiveDaysLast30Days),
        sum(col(Dic.colBroadcastTime)).as(Dic.colTotalTimeLast30Days),
        udfGetDays(max(col(Dic.colPlayEndTime)), lit(now)).as(Dic.colDaysFromLastActive),
        udfGetDays(min(col(Dic.colPlayEndTime)), lit(now)).as(Dic.colDaysSinceFirstActiveInTimewindow))


    val play_part_2 = plays
      .filter(
        col(Dic.colPlayEndTime).<(now)
          && col(Dic.colPlayEndTime).>=(pre_14))
      .groupBy(col(Dic.colUserId))
      .agg(
        countDistinct(col(Dic.colPlayEndTime)).as(Dic.colActiveDaysLast14Days),
        sum(col(Dic.colBroadcastTime)).as(Dic.colTotalTimeLast14Days)
      )


    val play_part_3 = plays
      .filter(
        col(Dic.colPlayEndTime).<(now)
          && col(Dic.colPlayEndTime).>=(pre_7))
      .groupBy(col(Dic.colUserId))
      .agg(
        countDistinct(col(Dic.colPlayEndTime)).as(Dic.colActiveDaysLast7Days),
        sum(col(Dic.colBroadcastTime)).as(Dic.colTotalTimeLast7Days)
      )
    val play_part_4 = plays
      .filter(
        col(Dic.colPlayEndTime).<(now)
          && col(Dic.colPlayEndTime).>=(pre_3))
      .groupBy(col(Dic.colUserId))
      .agg(
        countDistinct(col(Dic.colPlayEndTime)).as(Dic.colActiveDaysLast3Days),
        sum(col(Dic.colBroadcastTime)).as(Dic.colTotalTimeLast3Days)
      )
//
//    dataFrameList:+(play_part_1)
//    dataFrameList:+(play_part_2)
//    dataFrameList:+(play_part_3)
//    dataFrameList:+(play_part_4)
//    val joinKeysUserId = Seq(Dic.colUserId)
//    var play_part_result=result
//    for(elem <- dataFrameList){
//        play_part_result=play_part_result.join(elem,joinKeysUserId,"left")
//    }
//    play_part_result.show()

    val joinKeysUserId = Seq(Dic.colUserId)
    val result1=result.join(play_part_1,joinKeysUserId,"left")
    val result2 =result1.join(play_part_2, joinKeysUserId, "left")
    val result3 = result2.join(play_part_3, joinKeysUserId, "left")
    val result4 = result3.join(play_part_4, joinKeysUserId, "left")

    val joinKeyVideoId=Seq(Dic.colVideoId)
    val user_medias=plays.join(medias,joinKeyVideoId,"inner")

    val play_medias_part_11=user_medias
      .filter(
        col(Dic.colPlayEndTime).<(now)
          && col(Dic.colPlayEndTime).>=(pre_30)
          && col(Dic.colIsPaid).===(1))
      .groupBy(col(Dic.colUserId))
      .agg(
        sum(col(Dic.colBroadcastTime)).as(Dic.colTotalTimePaidVideosLast30Days)
      )

    val play_medias_part_12=user_medias
      .filter(
        col(Dic.colPlayEndTime).<(now)
          && col(Dic.colPlayEndTime).>=(pre_14)
          && col(Dic.colIsPaid).===(1))
      .groupBy(col(Dic.colUserId))
      .agg(
        sum(col(Dic.colBroadcastTime)).as(Dic.colTotalTimePaidVideosLast14Days)
      )

    val play_medias_part_13=user_medias
      .filter(
        col(Dic.colPlayEndTime).<(now)
          && col(Dic.colPlayEndTime).>=(pre_7)
          && col(Dic.colIsPaid).===(1))
      .groupBy(col(Dic.colUserId))
      .agg(
        sum(col(Dic.colBroadcastTime)).as(Dic.colTotalTimePaidVideosLast7Days)
      )

    val play_medias_part_14=user_medias
      .filter(
        col(Dic.colPlayEndTime).<(now)
          && col(Dic.colPlayEndTime).>=(pre_3)
          && col(Dic.colIsPaid).===(1))
      .groupBy(col(Dic.colUserId))
      .agg(
        sum(col(Dic.colBroadcastTime)).as(Dic.colTotalTimePaidVideosLast3Days)
      )

    val play_medias_part_15=user_medias
      .filter(
        col(Dic.colPlayEndTime).<(now)
          && col(Dic.colPlayEndTime).>=(pre_1)
          && col(Dic.colIsPaid).===(1))
      .groupBy(col(Dic.colUserId))
      .agg(
        sum(col(Dic.colBroadcastTime)).as(Dic.colTotalTimePaidVideosLast1Days)
      )

    val result5=result4.join(play_medias_part_11,joinKeysUserId,"left")
    val result6=result5.join(play_medias_part_12,joinKeysUserId, "left")
    val result7=result6.join(play_medias_part_13,joinKeysUserId,"left")
    val result8=result7.join(play_medias_part_14,joinKeysUserId, "left")
    val result9=result8.join(play_medias_part_15,joinKeysUserId, "left")




    val play_medias_part_21=user_medias
      .filter(
        col(Dic.colPlayEndTime).<(now)
          && col(Dic.colPlayEndTime).>=(pre_30)
          && !isnan(col(Dic.colPackageId)))
      .groupBy(col(Dic.colUserId))
      .agg(
        sum(col(Dic.colBroadcastTime)).as(Dic.colTotalTimeInPackageVideosLast30Days),
        stddev(col(Dic.colBroadcastTime)).as(Dic.colVarTimeInPackageVideosLast30Days),
        countDistinct(col(Dic.colVideoId)).as(Dic.colNumberInPackagesVideosLast30Days)
      )

    val play_medias_part_22=user_medias
      .filter(
        col(Dic.colPlayEndTime).<(now)
          && col(Dic.colPlayEndTime).>=(pre_14)
          && !isnan(col(Dic.colPackageId)))
      .groupBy(col(Dic.colUserId))
      .agg(
        sum(col(Dic.colBroadcastTime)).as(Dic.colTotalTimeInPackageVideosLast14Days),
        stddev(col(Dic.colBroadcastTime)).as(Dic.colVarTimeInPackageVideosLast14Days),
        countDistinct(col(Dic.colVideoId)).as(Dic.colNumberInPackagesVideosLast14Days)
      )

    val play_medias_part_23=user_medias
      .filter(
        col(Dic.colPlayEndTime).<(now)
          && col(Dic.colPlayEndTime).>=(pre_7)
          && !isnan(col(Dic.colPackageId)))
      .groupBy(col(Dic.colUserId))
      .agg(
        sum(col(Dic.colBroadcastTime)).as(Dic.colTotalTimeInPackageVideosLast7Days),
        stddev(col(Dic.colBroadcastTime)).as(Dic.colVarTimeInPackageVideosLast7Days),
        countDistinct(col(Dic.colVideoId)).as(Dic.colNumberInPackagesVideosLast7Days)
      )

    val play_medias_part_24=user_medias
      .filter(
        col(Dic.colPlayEndTime).<(now)
          && col(Dic.colPlayEndTime).>=(pre_3)
          && !isnan(col(Dic.colPackageId)))
      .groupBy(col(Dic.colUserId))
      .agg(
        sum(col(Dic.colBroadcastTime)).as(Dic.colTotalTimeInPackageVideosLast3Days),
        stddev(col(Dic.colBroadcastTime)).as(Dic.colVarTimeInPackageVideosLast3Days),
        countDistinct(col(Dic.colVideoId)).as(Dic.colNumberInPackagesVideosLast3Days)
      )

    val play_medias_part_25=user_medias
      .filter(
        col(Dic.colPlayEndTime).<(now)
          && col(Dic.colPlayEndTime).>=(pre_1)
          && !isnan(col(Dic.colPackageId)))
      .groupBy(col(Dic.colUserId))
      .agg(
        sum(col(Dic.colBroadcastTime)).as(Dic.colTotalTimeInPackageVideosLast1Days),
        stddev(col(Dic.colBroadcastTime)).as(Dic.colVarTimeInPackageVideosLast1Days),
        countDistinct(col(Dic.colVideoId)).as(Dic.colNumberInPackagesVideosLast1Days)
      )

    val result10=result9.join(play_medias_part_21,joinKeysUserId,"left")
    val result11=result10.join(play_medias_part_22,joinKeysUserId, "left")
    val result12=result11.join(play_medias_part_23,joinKeysUserId,"left")
    val result13=result12.join(play_medias_part_24,joinKeysUserId, "left")
    val result14=result13.join(play_medias_part_25,joinKeysUserId, "left")



    val play_medias_part_31=user_medias
      .filter(
        col(Dic.colPlayEndTime).<(now)
          && col(Dic.colPlayEndTime).>=(pre_30)
          && col(Dic.colVideoOneLevelClassification).===("幼儿"))
      .groupBy(col(Dic.colUserId))
      .agg(
        sum(col(Dic.colBroadcastTime)).as(Dic.colTotalTimeChildrenVideosLast30Days),
        countDistinct(col(Dic.colVideoId)).as(Dic.colNumberChildrenVideosLast30Days)
      )
    val play_medias_part_32=user_medias
      .filter(
        col(Dic.colPlayEndTime).<(now)
          && col(Dic.colPlayEndTime).>=(pre_14)
          && col(Dic.colVideoOneLevelClassification).===("幼儿"))
      .groupBy(col(Dic.colUserId))
      .agg(
        sum(col(Dic.colBroadcastTime)).as(Dic.colTotalTimeChildrenVideosLast14Days),
        countDistinct(col(Dic.colVideoId)).as(Dic.colNumberChildrenVideosLast14Days)
      )
    val play_medias_part_33=user_medias
      .filter(
        col(Dic.colPlayEndTime).<(now)
          && col(Dic.colPlayEndTime).>=(pre_7)
          && col(Dic.colVideoOneLevelClassification).===("幼儿"))
      .groupBy(col(Dic.colUserId))
      .agg(
        sum(col(Dic.colBroadcastTime)).as(Dic.colTotalTimeChildrenVideosLast7Days),
        countDistinct(col(Dic.colVideoId)).as(Dic.colNumberChildrenVideosLast7Days)
      )
    val play_medias_part_34=user_medias
      .filter(
        col(Dic.colPlayEndTime).<(now)
          && col(Dic.colPlayEndTime).>=(pre_3)
          && col(Dic.colVideoOneLevelClassification).===("幼儿"))
      .groupBy(col(Dic.colUserId))
      .agg(
        sum(col(Dic.colBroadcastTime)).as(Dic.colTotalTimeChildrenVideosLast3Days),
        countDistinct(col(Dic.colVideoId)).as(Dic.colNumberChildrenVideosLast3Days)
      )
    val play_medias_part_35=user_medias
      .filter(
        col(Dic.colPlayEndTime).<(now)
          && col(Dic.colPlayEndTime).>=(pre_1)
          && col(Dic.colVideoOneLevelClassification).===("幼儿"))
      .groupBy(col(Dic.colUserId))
      .agg(
        sum(col(Dic.colBroadcastTime)).as(Dic.colTotalTimeChildrenVideosLast1Days),
        countDistinct(col(Dic.colVideoId)).as(Dic.colNumberChildrenVideosLast1Days)
      )
    val result15=result14.join(play_medias_part_31,joinKeysUserId,"left")
    val result16=result15.join(play_medias_part_32,joinKeysUserId, "left")
    val result17=result16.join(play_medias_part_33,joinKeysUserId,"left")
    val result18=result17.join(play_medias_part_34,joinKeysUserId, "left")
    val result19=result18.join(play_medias_part_35,joinKeysUserId, "left")









    val userProfilePlayPartSavePath=hdfsPath+"data/predict/common/processed/userprofileplaypart"+now.split(" ")(0)
    //大约有85万用户
    result19.write.mode(SaveMode.Overwrite).format("parquet").save(userProfilePlayPartSavePath)




  }


  def main(args:Array[String]): Unit ={
    //val hdfsPath="hdfs:///pay_predict/"
    val hdfsPath=""
    val mediasProcessedPath=hdfsPath+"data/predict/common/processed/mediastemp"
    val playsProcessedPath=hdfsPath+"data/predict/common/processed/plays"
    val ordersProcessedPath=hdfsPath+"data/predict/common/processed/orders"
    val now=args(0)+" "+args(1)
    userProfileGeneratePlayPart(now,30,mediasProcessedPath,playsProcessedPath,ordersProcessedPath,hdfsPath)










  }

}
