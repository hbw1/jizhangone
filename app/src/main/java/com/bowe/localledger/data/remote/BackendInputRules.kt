package com.bowe.localledger.data.remote

const val CLOUD_USERNAME_MIN_LENGTH = 3
const val CLOUD_USERNAME_MAX_LENGTH = 64
const val CLOUD_PASSWORD_MIN_LENGTH = 6
const val CLOUD_PASSWORD_MAX_LENGTH = 128
const val CLOUD_DISPLAY_NAME_MIN_LENGTH = 1
const val CLOUD_DISPLAY_NAME_MAX_LENGTH = 64
const val CLOUD_DEVICE_NAME_MAX_LENGTH = 128
const val BOOK_NAME_MAX_LENGTH = 128
const val ENTITY_NAME_MAX_LENGTH = 64
const val NLP_RAW_TEXT_MAX_LENGTH = 4000

fun clampToMaxLength(value: String, maxLength: Int): String = value.take(maxLength)

fun normalizeBoundedText(value: String, maxLength: Int): String = value.trim().take(maxLength)

fun validateCloudLoginInput(username: String, password: String): String? {
    val normalizedUsername = username.trim()
    return when {
        normalizedUsername.isBlank() || password.isBlank() -> "请输入账号和密码"
        normalizedUsername.length !in CLOUD_USERNAME_MIN_LENGTH..CLOUD_USERNAME_MAX_LENGTH -> {
            "账号需为 $CLOUD_USERNAME_MIN_LENGTH-$CLOUD_USERNAME_MAX_LENGTH 个字符"
        }

        password.length !in CLOUD_PASSWORD_MIN_LENGTH..CLOUD_PASSWORD_MAX_LENGTH -> {
            "密码需为 $CLOUD_PASSWORD_MIN_LENGTH-$CLOUD_PASSWORD_MAX_LENGTH 个字符"
        }

        else -> null
    }
}

fun validateCloudRegisterInput(
    username: String,
    password: String,
    displayName: String,
): String? {
    val loginError = validateCloudLoginInput(username, password)
    if (loginError != null) return loginError

    val normalizedDisplayName = displayName.trim()
    return when {
        normalizedDisplayName.isBlank() -> "请完整填写注册信息"
        normalizedDisplayName.length !in CLOUD_DISPLAY_NAME_MIN_LENGTH..CLOUD_DISPLAY_NAME_MAX_LENGTH -> {
            "昵称需为 $CLOUD_DISPLAY_NAME_MIN_LENGTH-$CLOUD_DISPLAY_NAME_MAX_LENGTH 个字符"
        }

        else -> null
    }
}

fun maxLengthHint(maxLength: Int): String = "最多 $maxLength 个字符"

fun rangeLengthHint(minLength: Int, maxLength: Int): String = "$minLength-$maxLength 个字符"
