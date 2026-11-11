package dev.objz.commandbridge.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import dev.objz.commandbridge.net.proto.Envelope;
import dev.objz.commandbridge.net.proto.MessageType;

class InNodeTest {

    private static final Endpoint ENDPOINT = new Endpoint() {
        @Override
        public CompletableFuture<Void> send(Envelope env) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public boolean isOpen() {
            return true;
        }
    };

    /** Replies to every message it gets, which needs the send factory. */
    private static final class ReplyingHandler extends InboundHandler {
        final List<Envelope> received = new ArrayList<>();

        @Override
        public void accept(Endpoint endpoint, Envelope env) {
            received.add(env);
            reply(endpoint, env, MessageType.REGISTER_COMMANDS_RESULT, "ok");
        }
    }

    @Test
    void handlerRegisteredBeforeConnectingCanReply() throws Exception {
        InNode node = new InNode();
        ReplyingHandler handler = new ReplyingHandler();
        // The adapters register handlers first, then connect, which is when the factory is set.
        node.register(MessageType.REGISTER_COMMANDS, handler);

        List<Envelope> replies = new ArrayList<>();
        node.setSendOperationFactory((endpoint, envelope) -> {
            replies.add(envelope);
            return null;
        });

        Envelope request = Envelope.make(MessageType.REGISTER_COMMANDS, "proxy-1", "backend",
                Envelope.MAPPER.createObjectNode());
        node.onText(ENDPOINT, Envelope.MAPPER.writeValueAsString(request));

        assertEquals(1, handler.received.size());
        assertEquals(1, replies.size());
        assertSame(MessageType.REGISTER_COMMANDS_RESULT, replies.get(0).type());
    }

    @Test
    void replacingTheFactoryReachesRegisteredHandlers() throws Exception {
        InNode node = new InNode();
        ReplyingHandler handler = new ReplyingHandler();
        node.setSendOperationFactory((endpoint, envelope) -> null);
        node.register(MessageType.REGISTER_COMMANDS, handler);

        // A reconnect sets a new factory; the handler has to use it.
        List<Envelope> replies = new ArrayList<>();
        node.setSendOperationFactory((endpoint, envelope) -> {
            replies.add(envelope);
            return null;
        });

        Envelope request = Envelope.make(MessageType.REGISTER_COMMANDS, "proxy-1", "backend",
                Envelope.MAPPER.createObjectNode());
        node.onText(ENDPOINT, Envelope.MAPPER.writeValueAsString(request));

        assertEquals(1, replies.size());
    }
}
