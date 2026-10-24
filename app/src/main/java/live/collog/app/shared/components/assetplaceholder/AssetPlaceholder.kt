package live.collog.app.shared.components.assetplaceholder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import live.collog.app.shared.ui.designsystem.CollogColors

/** iOS `AssetPlaceholder(size:cornerRadius:)`: 정사각형 자리표시. */
@Composable
fun AssetPlaceholder(size: Dp, modifier: Modifier = Modifier, cornerRadius: Dp = 0.dp) {
    Box(modifier.size(size).background(CollogColors.assetPlaceholder, RoundedCornerShape(cornerRadius)))
}

/** iOS `AssetPlaceholder(width:height:cornerRadius:)`: [width]가 없으면 가로를 꽉 채운다. */
@Composable
fun AssetPlaceholder(
    modifier: Modifier = Modifier,
    width: Dp? = null,
    height: Dp? = null,
    cornerRadius: Dp = 0.dp,
) {
    val horizontal = if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()
    val sized = if (height != null) horizontal.height(height) else horizontal
    Box(modifier.then(sized).background(CollogColors.assetPlaceholder, RoundedCornerShape(cornerRadius)))
}
