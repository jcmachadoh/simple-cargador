package cu.jcmachadoh.simplecargador.data.update

import retrofit2.http.GET

interface UpdateApi {

    @GET("simplecargador_apk.json")
    suspend fun getLatestVersion(): UpdateInfo
}
