package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.model.AppLanguage
import com.example.data.model.SocialLink
import com.example.ui.theme.CyberCyan

/**
 * SnsDialog: Hộp thoại hiển thị danh sách các mạng xã hội & kênh kết nối GNZ (SNS).
 * Thiết kế cyberpunk, các nút bấm tách bạch, không lồng vào nhau, chạm là mở liên kết.
 */
@Composable
fun SnsDialog(
    socialLinks: List<SocialLink>,
    language: AppLanguage,
    onDismiss: () -> Unit
) {
    val isVi = language == AppLanguage.VIETNAMESE
    val context = LocalContext.current
    val openLabel = stringResource(if (isVi) R.string.sns_open_vi else R.string.sns_open_en)
    val openError = stringResource(if (isVi) R.string.sns_open_error_vi else R.string.sns_open_error_en)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xF207182E),
            border = BorderStroke(1.5.dp, CyberCyan),
            shadowElevation = 20.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 440.dp)
                .heightIn(min = 340.dp, max = 560.dp)
                .testTag("sns_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
            ) {
                // Header: Icon + Title + Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color(0xFF0D3B66),
                                            CyberCyan.copy(alpha = 0.35f)
                                        )
                                    )
                                )
                                .border(1.5.dp, CyberCyan, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Public,
                                contentDescription = stringResource(R.string.sns_channels_icon),
                                tint = CyberCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = stringResource(if (isVi) R.string.sns_title_vi else R.string.sns_title_en),
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = stringResource(if (isVi) R.string.sns_subtitle_vi else R.string.sns_subtitle_en),
                                color = CyberCyan,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(Color(0x22FFFFFF))
                            .testTag("close_sns_dialog_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(if (isVi) R.string.sns_close_vi else R.string.sns_close_en),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = stringResource(if (isVi) R.string.sns_description_vi else R.string.sns_description_en),
                    color = Color(0xFFD3E2F2),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Danh sách mạng xã hội
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("sns_links_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(socialLinks, key = { it.id }) { link ->
                        SnsLinkCard(
                            link = link,
                            openLabel = openLabel,
                            onOpen = { openUrl(context, link.url, openError) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Nút Đóng
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("dismiss_sns_dialog_button")
                ) {
                    Text(
                        text = stringResource(if (isVi) R.string.sns_close_vi else R.string.sns_close_en),
                        color = Color(0xFF001F3F),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun SnsLinkCard(
    link: SocialLink,
    openLabel: String,
    onOpen: () -> Unit
) {
    val (iconEmoji, accentColor) = when (link.id) {
        "x_twitter" -> "𝕏" to Color(0xFFE1E8ED)
        "truth_social" -> "🆃" to Color(0xFFFF5252)
        "reddit" -> "🔴" to Color(0xFFFF4500)
        "discord" -> "💬" to Color(0xFF5865F2)
        "snapchat" -> "👻" to Color(0xFFFFFC00)
        "locket" -> "💛" to Color(0xFFFFD54F)
        "linkedin" -> "💼" to Color(0xFF0A66C2)
        else -> "🌐" to CyberCyan
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C223D)),
        border = BorderStroke(1.dp, Color(0x334A90E2)),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .semantics(mergeDescendants = true) {
                contentDescription = "${link.name}, $openLabel"
            }
            .clickable(onClick = onOpen)
            .testTag("sns_item_${link.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.18f))
                    .border(1.dp, accentColor.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = iconEmoji, fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = link.name,
                    color = Color.White,
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("sns_name_${link.id}")
                )
                Text(
                    text = link.handle,
                    color = Color(0xFFC2D5E9),
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("sns_handle_${link.id}")
                )
                Spacer(modifier = Modifier.height(7.dp))
                Surface(shape = RoundedCornerShape(5.dp), color = CyberCyan.copy(alpha = 0.18f)) {
                    Text(
                        text = link.category,
                        color = Color(0xFF8FEAFF),
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                            .testTag("sns_category_${link.id}")
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = openLabel,
                tint = CyberCyan,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .padding(13.dp)
                    .size(20.dp)
            )
        }
    }
}

private fun openUrl(context: Context, url: String, errorMessage: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "$errorMessage: $url", Toast.LENGTH_SHORT).show()
    }
}
