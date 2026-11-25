package live.collog.app.features.timeline.views.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import live.collog.app.R
import live.collog.app.features.home.models.FamilyContact
import live.collog.app.features.timeline.viewmodels.TimelineViewModel
import live.collog.app.shared.models.familyRelationTitle
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.LocalViewerAccent
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.ViewerAccent
import live.collog.app.shared.ui.designsystem.pretendard
import live.collog.app.shared.ui.pressable

private val CenterSize = 88.dp
private val SideSize = 48.dp
private val SideWidth = 76.dp
private val HeaderSwipeDistance = 40.dp

/** iOS `ViewerProfileHeader.relationText(for:)`: 「나」는 본인, 가족은 관계 이름(없으면 가족). */
fun viewerRelationText(contact: FamilyContact): String =
    if (contact.id == TimelineViewModel.SelfContactId) "본인" else familyRelationTitle(contact.relation) ?: "가족"

/**
 * iOS `ViewerProfileHeader`: 리포트·타임라인 맨 위의 「지금 누구를 보는지」 머리말.
 * 큰 아바타 + 이름 + 관계 태그 + 점. 옆으로 밀거나 옆 사람을 눌러 바꾼다.
 * 가로로 미는 동작은 이 머리말 안에서만 사람을 바꾸고, 아래 주차 넘기기와는 겹치지 않는다.
 */
@Composable
fun ViewerProfileHeader(
    subjects: List<FamilyContact>,
    selectedId: String?,
    onSelect: (FamilyContact) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalViewerAccent.current
    val index = subjects.indexOfFirst { it.id == selectedId }
    val current = subjects.getOrNull(index) ?: return

    fun neighbor(delta: Int): FamilyContact? = subjects.getOrNull(index + delta)

    Column(
        modifier
            .fillMaxWidth()
            .pointerInput(subjects, selectedId) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragCancel = { total = 0f },
                    onDragEnd = {
                        if (abs(total) > HeaderSwipeDistance.toPx()) neighbor(if (total < 0) 1 else -1)?.let(onSelect)
                    },
                ) { _, amount -> total += amount }
            },
        verticalArrangement = Arrangement.spacedBy(Spacing.x2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.x3)) {
            SideSlot(neighbor(-1), leading = true, onSelect = onSelect)
            ViewerAvatar(current, CenterSize, isRinged = true)
            SideSlot(neighbor(1), leading = false, onSelect = onSelect)
        }

        Row(
            Modifier.semantics(mergeDescendants = true) {
                contentDescription = "${current.name}, ${viewerRelationText(current)} 리포트"
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
        ) {
            Text(
                current.name,
                style = pretendard(Pretendard.Bold, 24f, CollogColors.gray900),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                viewerRelationText(current),
                style = pretendard(Pretendard.SemiBold, 14f, CollogColors.gray00),
                modifier = Modifier
                    .background(if (accent == ViewerAccent.Family) CollogColors.greenNormal else accent.text, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }

        if (subjects.size > 1) {
            Row(
                Modifier.clearAndSetSemantics {},
                horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                subjects.forEachIndexed { offset, _ ->
                    Box(
                        Modifier
                            .size(width = if (offset == index) 22.dp else 8.dp, height = 8.dp)
                            .background(if (offset == index) accent.accent else CollogColors.gray400, CircleShape),
                    )
                }
            }
            val other = if (subjects.size == 2) neighbor(1) ?: neighbor(-1) else null
            Text(
                if (other != null) "옆으로 밀거나 “${other.name}”를 눌러 바꿔요" else "옆으로 밀어서 바꿔요",
                style = pretendard(Pretendard.Medium, 14f, CollogColors.gray800),
            )
        }
    }
}

@Composable
private fun SideSlot(contact: FamilyContact?, leading: Boolean, onSelect: (FamilyContact) -> Unit) {
    if (contact == null) {
        Spacer(Modifier.width(SideWidth).height(1.dp))
        return
    }
    Column(
        Modifier
            .width(SideWidth)
            .pressable { onSelect(contact) }
            .padding(vertical = Spacing.x2)
            .semantics(mergeDescendants = true) { contentDescription = "${contact.name} 리포트 보기" },
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (leading) CollogIcon("chevron.left", size = 18.dp, color = CollogColors.gray800)
            ViewerAvatar(contact, SideSize)
            if (!leading) CollogIcon("chevron.right", size = 18.dp, color = CollogColors.gray800)
        }
        Text(
            contact.name,
            style = pretendard(Pretendard.SemiBold, 14f, CollogColors.gray800),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * iOS `ViewerAvatar`: 관계에 맞는 3D 아바타(어머니·아버지·자녀·나). 없는 관계(배우자·형제 등)는 이름 첫 글자 원으로 둔다.
 * 지금 보는 사람([isRinged])은 링이 있고, [fullColor] 는 흐리게 하지 않고 원래 색 그대로(홈 머리말처럼 한 사람만 보일 때).
 */
@Composable
fun ViewerAvatar(
    contact: FamilyContact,
    size: Dp,
    modifier: Modifier = Modifier,
    isRinged: Boolean = false,
    fullColor: Boolean = false,
) {
    val accent = LocalViewerAccent.current
    val highlighted = isRinged || fullColor
    val ring = accent.accent.copy(alpha = 0.28f)
    Box(
        modifier
            .size(size)
            .then(
                if (isRinged) {
                    Modifier.drawBehind {
                        drawCircle(ring, radius = this.size.minDimension / 2 + 5.dp.toPx(), style = Stroke(4.dp.toPx()))
                    }
                } else {
                    Modifier
                },
            )
            .clip(CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        val asset = avatarAsset(contact)
        if (asset != null) {
            Image(
                painterResource(asset),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .background(CollogColors.gray100)
                    .alpha(if (highlighted) 1f else 0.85f),
            )
        } else {
            Box(
                Modifier.size(size).background(if (highlighted) accent.accent else CollogColors.gray400),
                contentAlignment = Alignment.Center,
            ) {
                Text(contact.name.take(1), style = pretendard(Pretendard.Bold, size.value * 0.42f, CollogColors.gray00))
            }
        }
    }
}

internal fun avatarAsset(contact: FamilyContact): Int? {
    if (contact.id == TimelineViewModel.SelfContactId) return R.drawable.avatar_child
    return when (contact.relation) {
        "MOTHER" -> R.drawable.avatar_mom
        "FATHER" -> R.drawable.avatar_dad
        "CHILD" -> R.drawable.avatar_child
        else -> null
    }
}

/** iOS `ViewerBadgeChip`: 접힌 위쪽 막대 오른쪽에 계속 남는 작은 「지금 보는 사람」 표시. */
@Composable
fun ViewerBadgeChip(name: String, modifier: Modifier = Modifier) {
    val accent = LocalViewerAccent.current
    Row(
        modifier
            .background(accent.light, CircleShape)
            .border(1.5.dp, accent.border, CircleShape)
            .padding(start = 5.dp, end = Spacing.x3, top = 5.dp, bottom = 5.dp)
            .semantics(mergeDescendants = true) { contentDescription = "지금 $name 리포트를 보고 있어요" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(22.dp).background(accent.accent, CircleShape), contentAlignment = Alignment.Center) {
            Text(name.take(1), style = pretendard(Pretendard.Bold, 12f, Color.White))
        }
        Text(
            name,
            style = pretendard(Pretendard.SemiBold, 14f, accent.text),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
