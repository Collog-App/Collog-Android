package live.collog.app.features.auth.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import live.collog.app.config.AppEnvironment
import live.collog.app.features.settings.models.HealthSelection
import live.collog.app.shared.services.api.ApiError
import live.collog.app.shared.services.api.ApiUser

enum class Gender(val rawValue: String, val title: String) {
    Male("MALE", "남성"),
    Female("FEMALE", "여성"),
    ;

    companion object {
        fun from(raw: String?): Gender? = entries.firstOrNull { it.rawValue == raw }
    }
}

/** 닉네임 칸 아래에 보이는 상태. [Unknown]은 확인 요청이 실패한 것으로, 저장할 때 서버가 다시 거른다. */
enum class NicknameStatus { Empty, Checking, Available, Taken, Invalid, Unknown }

/**
 * 피그마 「최종본」 회원가입(269:3930, 269:3888, 269:3902): 닉네임·성별·생년월일·초대 코드.
 * 저장은 `PATCH /v1/account/profile`, 코드를 넣었으면 이어서 `POST /v1/invitations/accept`.
 * 세션의 사용자는 두 단계가 모두 끝난 뒤에 한 번 바꾼다(코드가 틀려 머무는 동안 흐름이 다음 단계로 넘어가지 않게).
 */
class SignUpViewModel {
    var nickname by mutableStateOf("")
        private set
    var nicknameStatus by mutableStateOf(NicknameStatus.Empty)
        private set
    var gender by mutableStateOf<Gender?>(null)
    /** 숫자 8자리까지(YYYYMMDD). 화면은 YYYY-MM-DD 로 보인다. */
    var birthDigits by mutableStateOf("")
        private set
    var inviteCode by mutableStateOf("")
        private set
    var inviteError by mutableStateOf<String?>(null)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var isSubmitting by mutableStateOf(false)
        private set

    /** 가입을 이어서 할 때(나의 질환에서 뒤로) 저장된 값을 채운다. */
    fun prefill(user: ApiUser) {
        user.nickname?.let(::updateNickname)
        gender = Gender.from(user.gender)
        user.birthDate?.let { updateBirthDigits(it) }
    }

    fun updateNickname(value: String) {
        nickname = value
        nicknameStatus = if (value.isEmpty()) NicknameStatus.Empty else NicknameStatus.Checking
    }

    fun updateBirthDigits(value: String) {
        birthDigits = value.filter(Char::isDigit).take(8)
    }

    fun updateInviteCode(value: String) {
        inviteCode = value.filter(Char::isDigit).take(6)
        inviteError = null
    }

    val birthDate: LocalDate? get() = parseBirthDate(birthDigits)

    val birthDateError: String?
        get() = if (birthDigits.length == 8 && birthDate == null) "생년월일을 확인해주세요." else null

    val inviteCodeError: String?
        get() = inviteError ?: if (inviteCode.length in 1..5) "초대코드는 숫자 6자리예요." else null

    val canSubmit: Boolean
        get() = nicknameStatus in setOf(NicknameStatus.Available, NicknameStatus.Unknown) &&
            gender != null && birthDate != null && (inviteCode.isEmpty() || inviteCode.length == 6) && !isSubmitting

    /** 입력이 멈춘 뒤 부른다. 형식이 틀리면 서버에 묻지 않는다. */
    suspend fun checkNickname(environment: AppEnvironment) {
        val requested = nickname
        if (nicknameStatus != NicknameStatus.Checking) return
        if (!isValidNickname(requested)) {
            nicknameStatus = NicknameStatus.Invalid
            return
        }
        val status = try {
            val result = environment.api.nicknameAvailability(requested)
            when {
                result.available -> NicknameStatus.Available
                result.reason == "TAKEN" -> NicknameStatus.Taken
                else -> NicknameStatus.Invalid
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            NicknameStatus.Unknown
        }
        if (nickname == requested) nicknameStatus = status
    }

    /** 다 저장했으면 true. 닉네임이 겹치거나 코드가 틀리면 그 칸에 알리고 false. */
    suspend fun submit(environment: AppEnvironment): Boolean {
        val gender = gender ?: return false
        val birthDate = birthDate ?: return false
        if (!canSubmit) return false
        isSubmitting = true
        errorMessage = null
        inviteError = null
        val userId = environment.session.user?.id
        try {
            val saved = try {
                environment.api.updateAccountProfile(nickname, gender.rawValue, birthDate.toString())
            } catch (error: ApiError.Server) {
                if (error.status != 409) throw error
                nicknameStatus = NicknameStatus.Taken
                return false
            }
            var familyId = environment.session.familyId ?: saved.familyId
            if (inviteCode.isNotEmpty()) {
                try {
                    familyId = environment.api.acceptInvitation(inviteCode).familyId
                } catch (error: ApiError.Server) {
                    inviteError = error.message
                    return false
                }
            }
            if (environment.session.user?.id != userId) return false
            environment.session.updateUser(saved.copy(familyId = familyId))
            return true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorMessage = error.message
            return false
        } finally {
            isSubmitting = false
        }
    }

    companion object {
        /** 서버 `NICKNAME_PATTERN`: 완성형 한글·영문·숫자 2~10자, 공백 없음. */
        private val NicknamePattern = Regex("^[가-힣A-Za-z0-9]{2,10}$")
        private val Seoul = ZoneId.of("Asia/Seoul")

        fun isValidNickname(value: String): Boolean = NicknamePattern.matches(value)

        /** "19940315" → 1994-03-15. 서버처럼 1900-01-01 ~ 오늘(한국 시간)만 받는다. */
        fun parseBirthDate(digits: String, today: LocalDate = LocalDate.now(Seoul)): LocalDate? {
            if (digits.length != 8 || !digits.all(Char::isDigit)) return null
            val date = try {
                LocalDate.of(digits.take(4).toInt(), digits.substring(4, 6).toInt(), digits.substring(6).toInt())
            } catch (error: DateTimeException) {
                return null
            }
            return date.takeIf { !it.isBefore(LocalDate.of(1900, 1, 1)) && !it.isAfter(today) }
        }
    }
}

/**
 * 피그마 「최종본」 나의 질환(269:3917) 저장: `PUT /v1/parents/{나}/profile` {conditions, concerns}.
 * 서버는 본인의 분석 동의가 있어야 받는다(약관 단계에서 받았다). 저장 뒤에도 같은 계정이면 true.
 */
internal suspend fun saveOwnConditions(environment: AppEnvironment, selection: HealthSelection, concerns: String): Boolean {
    val userId = environment.session.user?.id ?: throw ApiError.Unauthenticated
    environment.api.updateProfile(userId, selection.values, concerns.trim())
    return environment.session.user?.id == userId
}
