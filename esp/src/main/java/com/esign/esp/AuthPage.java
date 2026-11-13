package com.esign.esp;

import com.esign.common.TxnRef;
import com.esign.common.error.ApiException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AuthPage {
    private final EsignGatewayService gateway;

    public AuthPage(EsignGatewayService gateway) {
        this.gateway = gateway;
    }

    public String page(String txnref) {
        try {
            String[] parts = TxnRef.decode(txnref);
            EsignTransaction transaction = gateway.transaction(parts[0]);
            if (!transaction.resCode.equals(parts[1])) {
                return message("This signing request was not found.");
            }
            String signer = transaction.requestSignerId == null ? "" : transaction.requestSignerId;
            boolean locked = transaction.requestSignerId != null;
            StringBuilder docs = new StringBuilder();
            for (EsignTransaction.DocInput document : transaction.documents) {
                docs.append("<label class=\"doc\"><input type=\"checkbox\" name=\"doc\" value=\"")
                        .append(esc(document.id()))
                        .append("\" checked><span><strong>")
                        .append(esc(document.docInfo()))
                        .append("</strong><a href=\"").append(esc(document.docUrl()))
                        .append("\" target=\"_blank\" rel=\"noopener\">View document</a>")
                        .append("<code>SHA-256 ").append(esc(document.hashHex())).append("</code></span></label>");
            }
            String body = """
                    <p class="kicker">Authentication</p>
                    <h1>Confirm and sign</h1>
                    <p class="lead">Transaction %s. Review the document, then enter the signer id, PIN, and one-time password. Uncheck a document to cancel that document only.</p>
                    <form class="card" method="post" action="/api/v1/esign/auth">
                      <input type="hidden" name="txnref" value="%s">
                      <label>Signer id <input name="signerId" value="%s" %s required></label>
                      <div class="row">
                        <label>PIN <input name="pin" type="password" autocomplete="current-password" required></label>
                        <label>One-time password <input name="otp" inputmode="numeric" autocomplete="one-time-code" required></label>
                      </div>
                      %s
                      <p id="otp" class="notice" hidden></p>
                      <div class="actions">
                        <button class="primary" type="submit">Sign document</button>
                        <button class="secondary" type="button" id="send">Send one-time password</button>
                      </div>
                    </form>
                    <script>
                      document.getElementById('send').onclick = async () => {
                        const signerId = document.querySelector('[name=signerId]').value;
                        const note = document.getElementById('otp');
                        note.hidden = false;
                        note.textContent = 'Requesting a one-time password…';
                        const response = await fetch('/api/v1/esign/auth/otp', {
                          method: 'POST', headers: {'Content-Type': 'application/json'},
                          body: JSON.stringify({txnref: '%s', signerId})
                        });
                        const body = await response.json();
                        note.textContent = body.demoOtp
                          ? 'Demo password, no SMS is sent: ' + body.demoOtp
                          : (body.errorMessage || 'Password sent.');
                      };
                    </script>
                    """.formatted(esc(transaction.txn), esc(txnref), esc(signer),
                    locked ? "readonly" : "", docs, esc(txnref));
            return shell("Confirm signature", body);
        } catch (RuntimeException exception) {
            return message("This signing request was not found.");
        }
    }

    public String submit(String txnref, String signerId, String pin, String otp, List<String> docs) {
        try {
            EsignGatewayService.AuthResult result = gateway.authenticate(
                    txnref, signerId, pin, otp, docs == null ? List.of() : docs);
            EsignTransaction transaction = result.transaction();
            if (transaction.redirectUrl != null) {
                String ref = TxnRef.encode(transaction.txn, transaction.resCode);
                String body = """
                        <p class="kicker">Authentication</p>
                        <h1>Returning to the application</h1>
                        <p class="lead">The signature is complete. This page sends you back to the application provider.</p>
                        <form id="back" method="post" action="%s">
                          <input type="hidden" name="txnref" value="%s">
                        </form>
                        <script>document.getElementById('back').submit()</script>
                        """.formatted(esc(transaction.redirectUrl), esc(ref));
                return shell("Returning", body);
            }
            return message("Signing finished with status " + transaction.status
                    + (transaction.error.isBlank() ? "" : " error " + transaction.error) + ".");
        } catch (ApiException exception) {
            return message(exception.errorCode().code() + " " + exception.getMessage());
        }
    }

    private static String message(String text) {
        return shell("Notice", "<p class=\"kicker\">Service provider</p><h1>Unable to continue</h1><p class=\"lead\">"
                + esc(text) + "</p>");
    }

    private static String shell(String title, String body) {
        return """
                <!DOCTYPE html>
                <html lang="en"><head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>""" + esc(title) + """
                </title>
                <link rel="stylesheet" href="/site.css">
                </head><body>
                <header class="bar"><a class="brand" href="/"><span class="mark" aria-hidden="true">e</span> eSign <span>Service provider</span></a><nav class="bar-nav"><a href="/#operations">Operations</a><a href="/#logs">Logs</a><a href="/docs.html">Documentation</a><span class="spec">API 3.3</span></nav></header>
                <main>""" + body + """
                </main>
                </body></html>
                """;
    }

    private static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
