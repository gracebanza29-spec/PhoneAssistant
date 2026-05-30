package com.phoneassistant.data.api

import com.google.gson.annotations.SerializedName
import com.phoneassistant.data.model.CallerInfo
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class NumLookupResponse(
    @SerializedName("valid")       val valid: Boolean,
    @SerializedName("carrier")     val carrier: String?,
    @SerializedName("line_type")   val lineType: String?,
    @SerializedName("location")    val location: String?,
    @SerializedName("country_name") val countryName: String?
)

interface NumLookupApi {
    @GET("v1/validate")
    suspend fun validate(
        @Query("apikey") apiKey: String,
        @Query("number") number: String
    ): NumLookupResponse
}

object ApiClient {
    val api: NumLookupApi = Retrofit.Builder()
        .baseUrl("https://api.numlookupapi.com/")
        .client(OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build())
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(NumLookupApi::class.java)
}

fun NumLookupResponse.toCallerInfo(number: String): CallerInfo {
    val lineLabel = when (lineType) {
        "mobile"       -> "📱 Mobile"
        "fixed_line"   -> "☎️ Fixe"
        "voip"         -> "🌐 VoIP"
        "toll_free"    -> "🟢 Numéro vert"
        "premium_rate" -> "⚠️ Numéro surtaxé"
        else           -> lineType
    }
    val loc = listOfNotNull(location, countryName)
        .filter { it.isNotBlank() }
        .joinToString(", ")
        .takeIf { it.isNotEmpty() }

    return CallerInfo(
        number = number,
        carrier = carrier,
        lineType = lineLabel,
        location = loc,
        isSpam = lineType == "premium_rate"
    )
}
