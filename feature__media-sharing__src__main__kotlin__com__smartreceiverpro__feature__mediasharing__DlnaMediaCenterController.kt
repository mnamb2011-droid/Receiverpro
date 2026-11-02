package com.smartreceiverpro.feature.mediasharing

import com.smartreceiverpro.core.common.AppResult
import com.smartreceiverpro.core.common.ErrorCode
import com.smartreceiverpro.core.network.UpnpSoapHttpClient
import com.smartreceiverpro.engine.api.MediaSharingCapabilities

/**
 * Implements Section 14 DLNA Media Center:
 * - Direction 1 (Phone -> Receiver DMR): Pushes video/audio/image URLs via UPnP AVTransport
 *   after verifying MIME type compatibility.
 * - Direction 2 (External UPnP Server -> Phone DMP): Queries ContentDirectory:1 Browse action.
 */
class DlnaMediaCenterController(
    private val soapClient: UpnpSoapHttpClient = UpnpSoapHttpClient()
) {
    companion object {
        const val AV_TRANSPORT_SERVICE = "urn:schemas-upnp-org:service:AVTransport:1"
        const val CONTENT_DIR_SERVICE = "urn:schemas-upnp-org:service:ContentDirectory:1"
    }

    suspend fun pushMediaToReceiverDmr(
        capabilities: MediaSharingCapabilities,
        mediaHttpUri: String,
        mimeType: String,
        title: String
    ): AppResult<Unit> {
        if (!capabilities.supportsDlnaDmrPush) {
            return AppResult.UnsupportedCapability("DLNA_DMR", "الرسيفر المتصل لا يدعم استقبال الوسائط عبر DLNA DMR.")
        }
        if (capabilities.supportedMimeTypes.isNotEmpty() &&
            capabilities.supportedMimeTypes.none { it.equals(mimeType, ignoreCase = true) }
        ) {
            return AppResult.Error(
                ErrorCode.UNSUPPORTED_OPERATION,
                "صيغة الملف ($mimeType) غير مدعومة من قِبل مشغل الرسيفر."
            )
        }
        val controlUrl = capabilities.avTransportControlUrl
            ?: return AppResult.Error(ErrorCode.PROTOCOL_MISMATCH, "عنوان التحكم AVTransport غير متاح.")

        val setUriArgs = """
            <InstanceID>0</InstanceID>
            <CurrentURI>$mediaHttpUri</CurrentURI>
            <CurrentURIMetaData></CurrentURIMetaData>
        """.trimIndent()

        val setUriResult = soapClient.invokeSoapAction(controlUrl, AV_TRANSPORT_SERVICE, "SetAVTransportURI", setUriArgs)
        if (setUriResult !is AppResult.Success) return setUriResult as AppResult.Error

        val playArgs = "<InstanceID>0</InstanceID><Speed>1</Speed>"
        val playResult = soapClient.invokeSoapAction(controlUrl, AV_TRANSPORT_SERVICE, "Play", playArgs)
        return when (playResult) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Error -> playResult
            is AppResult.UnsupportedCapability -> playResult
        }
    }

    suspend fun browseExternalMediaServerDmp(contentDirectoryUrl: String, objectId: String = "0"): AppResult<String> {
        val browseArgs = """
            <ObjectID>$objectId</ObjectID>
            <BrowseFlag>BrowseDirectChildren</BrowseFlag>
            <Filter>*</Filter>
            <StartingIndex>0</StartingIndex>
            <RequestedCount>50</RequestedCount>
            <SortCriteria></SortCriteria>
        """.trimIndent()
        return soapClient.invokeSoapAction(contentDirectoryUrl, CONTENT_DIR_SERVICE, "Browse", browseArgs)
    }
}
