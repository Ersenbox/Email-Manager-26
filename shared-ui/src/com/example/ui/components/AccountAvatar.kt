package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.common.data.EmailAccountEntity
import com.example.common.i18n.t
import com.example.ui.platform.decodeImage
import java.io.File

/** Hesabın resmi yoksa baş harfi gösterilir; sunucu girişi doğrulandıysa rozet eklenir. */
@Composable
fun AccountAvatar(
    account: EmailAccountEntity,
    size: Dp,
    modifier: Modifier = Modifier,
    showBadge: Boolean = true
) {
    val photoFile = account.photoPath?.let { File(it) }
    // Dosya aynı adla değişebildiği için değişim zamanı da anahtardır
    val bitmap = remember(account.photoPath, photoFile?.lastModified()) {
        photoFile?.takeIf { it.exists() }?.let { decodeImage(it.absolutePath) }
    }
    Box(modifier = modifier.size(size)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = t("Profil resmi"),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape)
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = account.emailAddress.take(1).uppercase(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.44f).sp
                )
            }
        }
        if (showBadge && (account.verifiedAt > 0L || account.googleVerifiedAt > 0L)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.42f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = t("Hesap doğrulandı"),
                    tint = Color(0xFF16A34A),
                    modifier = Modifier.size(size * 0.36f)
                )
            }
        }
    }
}
