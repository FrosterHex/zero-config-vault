package com.vault.configmanager.config;

import com.vault.configmanager.model.VaultDocument;
import com.vault.configmanager.util.JsonTraverser;
import org.bson.Document;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertEvent;
import org.springframework.stereotype.Component;

/**
 * MongoDB Lifecycle Event Listener for VaultDocument.
 * Intercepts persistence events to ensure field-level encryption is enforced:
 * - On before-convert: Automatically encrypts any plaintext values in "secure_settings"
 *   before the document is converted and written to MongoDB.
 */
@Component
public class VaultDocumentLifecycleListener extends AbstractMongoEventListener<VaultDocument> {

    private final JsonTraverser jsonTraverser;

    public VaultDocumentLifecycleListener(JsonTraverser jsonTraverser) {
        this.jsonTraverser = jsonTraverser;
    }

    @Override
    public void onBeforeConvert(BeforeConvertEvent<VaultDocument> event) {
        VaultDocument document = event.getSource();
        if (document != null && document.getPayload() != null) {
            Document encryptedPayload = jsonTraverser.encryptDocument(document.getPayload());
            document.setPayload(encryptedPayload);
        }
    }
}
