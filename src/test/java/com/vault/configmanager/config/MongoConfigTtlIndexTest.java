package com.vault.configmanager.config;

import com.mongodb.MongoCommandException;
import com.mongodb.ServerAddress;
import com.mongodb.client.ListIndexesIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import org.bson.BsonDocument;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoConfigTtlIndexTest {

    private MongoDatabase db;
    private MongoCollection<Document> configurations;
    private MongoConfig config;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        db = mock(MongoDatabase.class);
        configurations = mock(MongoCollection.class);
        when(db.getCollection("configurations")).thenReturn(configurations);
        config = new MongoConfig(mock(MongoClient.class));
    }

    @SuppressWarnings("unchecked")
    private void existingIndex(long seconds) {
        Document idx = new Document("name", "expiresAt_1")
                .append("key", new Document("expiresAt", 1))
                .append("expireAfterSeconds", seconds);
        ListIndexesIterable<Document> iterable = mock(ListIndexesIterable.class);
        MongoCursor<Document> cursor = mock(MongoCursor.class);
        when(configurations.listIndexes()).thenReturn(iterable);
        when(iterable.iterator()).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn(idx);
        // for-each over Iterable uses iterator(); forEach default also does
    }

    private MongoCommandException unauthorized() {
        return new MongoCommandException(new BsonDocument("ok", new org.bson.BsonDouble(0))
                .append("code", new org.bson.BsonInt32(13)).append("errmsg", new org.bson.BsonString("unauthorized")),
                new ServerAddress());
    }

    @Test
    void collModUnauthorized_dropsAndRecreatesIndex() {
        existingIndex(7776000L);
        when(db.runCommand(any(Document.class))).thenThrow(unauthorized());

        config.ensureExpiryTtlIndex(db);

        verify(configurations).dropIndex("expiresAt_1");
        ArgumentCaptor<IndexOptions> options = ArgumentCaptor.forClass(IndexOptions.class);
        verify(configurations).createIndex(eq(new Document("expiresAt", 1)), options.capture());
        assertEquals(0L, options.getValue().getExpireAfter(TimeUnit.SECONDS));
    }

    @Test
    void collModSucceeds_noDropFallback() {
        existingIndex(7776000L);

        config.ensureExpiryTtlIndex(db);

        verify(db).runCommand(any(Document.class));
        verify(configurations, never()).dropIndex(any(String.class));
    }

    @Test
    void fallbackFails_doesNotThrow() {
        existingIndex(7776000L);
        when(db.runCommand(any(Document.class))).thenThrow(unauthorized());
        doThrow(new IllegalStateException("nope")).when(configurations).dropIndex(any(String.class));

        config.ensureExpiryTtlIndex(db);

        verify(configurations, never()).createIndex(any(org.bson.conversions.Bson.class), any(IndexOptions.class));
    }
}
