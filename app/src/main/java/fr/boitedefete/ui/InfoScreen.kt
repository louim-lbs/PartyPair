package fr.boitedefete.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.boitedefete.BuildConfig
import fr.boitedefete.R

const val REPO_URL = "https://github.com/louim-lbs/PartyPair"
const val ISSUES_URL = "$REPO_URL/issues"
const val LICENSE_URL = "$REPO_URL/blob/main/LICENSE"

/** Page d'informations : version, liens vers le depot, licence. */
@Composable
fun InfoScreen(
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Party.Cabinet)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 40.dp)
    ) {
        Text(
            stringResource(R.string.app_name).uppercase(),
            style = Display.copy(fontSize = 22.sp),
            color = Party.Silkscreen
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.info_tagline),
            style = Body,
            color = Party.Muted
        )

        Spacer(Modifier.height(28.dp))
        InfoRow(stringResource(R.string.info_version), BuildConfig.VERSION_NAME)
        InfoRow(stringResource(R.string.info_build), BuildConfig.BUILD_DATE)
        InfoRow(stringResource(R.string.info_license), "MIT")

        Spacer(Modifier.height(24.dp))
        LinkButton(stringResource(R.string.info_repo)) { onOpenUrl(REPO_URL) }
        LinkButton(stringResource(R.string.info_issues)) { onOpenUrl(ISSUES_URL) }
        LinkButton(stringResource(R.string.info_license_link)) { onOpenUrl(LICENSE_URL) }

        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(R.string.info_disclaimer),
            style = Body.copy(fontSize = 13.sp),
            color = Party.Muted
        )

        Spacer(Modifier.height(32.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.info_back).uppercase(),
                style = Silkscreen,
                color = Party.Orange
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = Silkscreen.copy(fontSize = 13.sp), color = Party.Muted)
        Text(value, style = Body, color = Party.Silkscreen)
    }
}

@Composable
private fun LinkButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = Silkscreen.copy(fontSize = 14.sp),
        color = Party.Orange,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp)
    )
}
