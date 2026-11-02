package com.smartreceiverpro.core.network

import com.smartreceiverpro.core.common.AppResult
import com.smartreceiverpro.core.common.ErrorCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Executes UPnP AV / DLNA SOAP actions (AVTransport, RenderingControl, ContentDirectory)
 * and fetches UPnP Device Description XML documents.
 */
class UpnpSoapHttpClient {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    suspend fun fetchDescriptionXml(url: String): AppResult<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext AppResult.Error(
                        ErrorCode.PROTOCOL_MISMATCH,
                        "فشل جلب ملف وصف UPnP (HTTP ${response.code})"
                    )
                }
                AppResult.Success(response.body?.string().orEmpty())
            }
        } catch (e: Exception) {
            AppResult.Error(ErrorCode.NETWORK_UNREACHABLE, "تعذر قراءة وصف UPnP: ${e.message}", e)
        }
    }

    suspend fun invokeSoapAction(
        controlUrl: String,
        serviceType: String,
        actionName: String,
        innerXmlArguments: String
    ): AppResult<String> = withContext(Dispatchers.IO) {
        val soapEnvelope = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:$actionName xmlns:u="$serviceType">
                        $innerXmlArguments
                    </u:$actionName>
                </s:Body>
            </s:Envelope>
        """.trimIndent()

        try {
            val request = Request.Builder()
                .url(controlUrl)
                .addHeader("Content-Type", "text/xml; charset=\"utf-8\"")
                .addHeader("SOAPAction", "\"$serviceType#$actionName\"")
                .post(soapEnvelope.toRequestBody("text/xml; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext AppResult.Error(
                        ErrorCode.PROTOCOL_MISMATCH,
                        "رفض الجهاز أمر UPnP $actionName (HTTP ${response.code})"
                    )
                }
                AppResult.Success(bodyStr)
            }
        } catch (e: Exception) {
            AppResult.Error(ErrorCode.CONNECTION_TIMEOUT, "فشل إرسال أمر UPnP $actionName: ${e.message}", e)
        }
    }
}
