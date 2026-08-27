package com.safevault.app.ui.screens.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.safevault.app.R

/**
 * Full-screen biometric lock gate. It is intentionally stateless: the hosting
 * Activity owns the [BiometricPrompt] (it needs a FragmentActivity), so this
 * composable just renders the locked state and delegates the unlock action.
 *
 * [errorMessage] surfaces a non-cancellation failure (e.g. no credential
 * enrolled) so the user understands why the vault will not open.
 *
 * [onEnrollClick] is non-null only when nothing is enrolled; it sends the user
 * to system security settings, which is the only place the problem is fixable.
 */
@Composable
fun LockScreen(
    errorMessage: String?,
    onUnlockClick: () -> Unit,
    onEnrollClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = null,
            modifier = Modifier.size(88.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.lock_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = stringResource(R.string.lock_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        Button(
            onClick = onUnlockClick,
            modifier = Modifier.padding(top = 40.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Fingerprint,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.lock_unlock),
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        if (onEnrollClick != null) {
            TextButton(
                onClick = onEnrollClick,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(text = stringResource(R.string.lock_open_security_settings))
            }
        }
    }
}
