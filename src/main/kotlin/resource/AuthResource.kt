package resource

import com.srw.util.injectLazy
import io.ktor.resources.Resource
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import model.request.BasicAuthRequest
import model.request.NfcAuthRequest
import model.request.RefreshTokenAuthRequest
import service.AuthService
import kotlin.getValue

@Resource("/auth")
class AuthResource {
    @Resource("login")
    class Login(val parent: AuthResource = AuthResource()) {
        @Resource("admin")
        class Admin(val parent: Login = Login())
        @Resource("client")
        class Client(val parent: Login = Login())
        @Resource("agent")
        class Agent(val parent: Login = Login())
    }
    @Resource("logout")
    class Logout(val parent: AuthResource = AuthResource())
    @Resource("refresh")
    class Refresh(val parent: AuthResource = AuthResource())
}

fun Route.authResources() {
    val authService by injectLazy<AuthService>()
    post<AuthResource.Login.Admin> {
        val body = call.receive<BasicAuthRequest>()
        val (code, response) = authService.login(
            authType = AuthService.AuthType.Admin(
                username = body.username,
                password = body.password
            )
        )
        call.respond(code, response)
    }
    post<AuthResource.Login.Agent> {
        val body = call.receive<BasicAuthRequest>()
        val (code, response) = authService.login(
            authType = AuthService.AuthType.Agent(
                username = body.username,
                password = body.password
            )
        )
        call.respond(code, response)
    }
    post<AuthResource.Login.Client> {
        val body = call.receive<NfcAuthRequest>()
        val (code, response) = authService.login(
            authType = AuthService.AuthType.Client(
                nfc = body.nfc
            )
        )
        call.respond(code, response)
    }
    post<AuthResource.Logout> {
        val body = call.receive<RefreshTokenAuthRequest>()
        val (code, response) = authService.logout(body.refreshToken)
        call.respond(code, response)
    }
    post<AuthResource.Refresh> {
        val body = call.receive<RefreshTokenAuthRequest>()
        val (code, response) = authService.refresh(body.refreshToken)
        call.respond(code, response)
    }
}