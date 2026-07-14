import pathlib

replacements = [
    # AuthServiceImpl.java
    (
        r'ydsz-pmis-backend\ydsz-pmis-userinfo\ydsz-pmis-userinfo-server\src\main\java\com\njydsz\pmis\userinfo\server\service\impl\AuthServiceImpl.java',
        [
            ('CryptoUtil.verifyPasswordBCrypt', 'PwdUtils.verifyPasswordBCrypt'),
            ('CryptoUtil.verifyPassword(', 'PwdUtils.verifyPasswordWithSha256Salt('),
            ('CryptoUtil.hashPasswordBCrypt', 'PwdUtils.hashPasswordBCrypt'),
            ('TraceIdUtil', 'TracerUtils'),
        ]
    ),
    # UserAccountServiceImpl.java
    (
        r'ydsz-pmis-backend\ydsz-pmis-userinfo\ydsz-pmis-userinfo-server\src\main\java\com\njydsz\pmis\userinfo\server\service\impl\UserAccountServiceImpl.java',
        [
            ('import com.njydsz.pmis.common.util.CryptoUtil;', 'import com.njydsz.pmis.common.util.security.PwdUtils;'),
            ('CryptoUtil.isBCryptFormat', 'PwdUtils.isBCryptFormat'),
            ('CryptoUtil.verifyPasswordBCrypt', 'PwdUtils.verifyPasswordBCrypt'),
            ('CryptoUtil.verifyPassword(', 'PwdUtils.verifyPasswordWithSha256Salt('),
            ('CryptoUtil.hashPasswordBCrypt', 'PwdUtils.hashPasswordBCrypt'),
        ]
    ),
    # UnsubscribeTokenUtil.java
    (
        r'ydsz-pmis-backend\ydsz-pmis-message\ydsz-pmis-message-server\src\main\java\com\njydsz\pmis\message\server\token\UnsubscribeTokenUtil.java',
        [
            ('import com.njydsz.pmis.common.util.CryptoUtil;', 'import com.njydsz.pmis.common.util.security.DigestUtils;\nimport java.util.Base64;'),
            ('CryptoUtil.base64UrlEncode(', 'Base64.getUrlEncoder().withoutPadding().encodeToString('),
            ('CryptoUtil.base64UrlDecode(', 'Base64.getUrlDecoder().decode('),
            ('CryptoUtil.constantTimeEquals(', 'DigestUtils.constantTimeEquals('),
            ('CryptoUtil.hmacSha256(', 'DigestUtils.hmacSha256UrlSafe('),
        ]
    ),
    # DingTalkChannel.java
    (
        r'ydsz-pmis-backend\ydsz-pmis-message\ydsz-pmis-message-server\src\main\java\com\njydsz\pmis\message\server\channel\DingTalkChannel.java',
        [
            ('import com.njydsz.pmis.common.util.CryptoSignUtil;', 'import com.njydsz.pmis.common.util.security.DigestUtils;'),
            ('CryptoSignUtil.hmacSha256Base64(', 'DigestUtils.hmacSha256Base64('),
        ]
    ),
    # DingTalkSignatureUtil.java
    (
        r'ydsz-pmis-backend\ydsz-pmis-workflow\ydsz-pmis-workflow-server\src\main\java\com\njydsz\pmis\workflow\server\thirdparty\DingTalkSignatureUtil.java',
        [
            ('import com.njydsz.pmis.common.util.CryptoSignUtil;', 'import com.njydsz.pmis.common.util.security.DigestUtils;'),
            ('CryptoSignUtil.verifySignature(', 'DigestUtils.verifySignature('),
            ('CryptoSignUtil.SignatureEncoding.BASE64', 'DigestUtils.SignatureEncoding.BASE64'),
        ]
    ),
]

base = pathlib.Path(r'd:\Code\ydsz\ydsz-pmis')
for relpath, subs in replacements:
    fpath = base / relpath
    if not fpath.exists():
        print(f'SKIP (not found): {relpath}')
        continue
    text = fpath.read_text(encoding='utf-8')
    for old, new in subs:
        text = text.replace(old, new)
    fpath.write_text(text, encoding='utf-8')
    print(f'OK: {relpath}')
