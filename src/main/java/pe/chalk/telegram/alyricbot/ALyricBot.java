package pe.chalk.telegram.alyricbot;

import de.vivistra.telegrambot.client.BotRequest;
import de.vivistra.telegrambot.model.message.AudioMessage;
import de.vivistra.telegrambot.model.message.Message;
import de.vivistra.telegrambot.model.message.MessageType;
import de.vivistra.telegrambot.model.message.TextMessage;
import de.vivistra.telegrambot.receiver.IReceiverService;
import de.vivistra.telegrambot.receiver.Receiver;
import de.vivistra.telegrambot.sender.Sender;
import de.vivistra.telegrambot.settings.BotSettings;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MIME;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * @author ChalkPE <chalkpe@gmail.com>
 * @since 2015-10-05
 */
public class ALyricBot implements IReceiverService {
    public static String TOKEN = "";
    public static final ContentType CONTENT_TYPE = ContentType.create(ContentType.TEXT_PLAIN.getMimeType(), MIME.UTF8_CHARSET);

    private LyricManager manager;

    @FunctionalInterface
    public interface Factory<T> {
        T get() throws Throwable;
    }

    public static void main(String[] args) throws IOException {
        new ALyricBot(args[0]);
    }

    public ALyricBot(String token) throws IOException {
        ALyricBot.TOKEN = token;
        this.manager = new LyricManager(Paths.get("cache"));

        BotSettings.setApiToken(ALyricBot.TOKEN);
        Receiver.subscribe(this);
    }

    public void received(Message message){
        if(message.getMessageType() == MessageType.TEXT_MESSAGE){
            String[] commands = message.getMessage().toString().split(" ");
            if(commands[0].contains("@")){
                commands[0] = commands[0].split("@")[0];
            }

            if(!commands[0].equalsIgnoreCase("/lyric")){
                return;
            }

            if(commands.length <= 1){
                ALyricBot.reply(message, "Developed by @ChalkPE\n\nUsage: /lyric <URL OF MUSIC FILE>");
                return;
            }

            if(!commands[1].startsWith("http://") && !commands[1].startsWith("https://")){
                commands[1] = "http://" + commands[1];
            }
            final String url = String.join(" ", Arrays.copyOfRange(commands, 1, commands.length));

            this.search(message, () -> this.manager.getHash(message, url));
        }else if(message.getMessageType() == MessageType.AUDIO_MESSAGE){
            this.search(message, () -> this.manager.getHash((AudioMessage) message));
        }
    }

    public void search(final Message message, final Factory<String> hashFactory){
        new Thread(() -> {
            try{
                /* #1 - DOWNLOAD AUDIO FILE FROM STREAM */
                ALyricBot.reply(message, "⚫️⚪️⚪️  Getting an information...");
                String hash = hashFactory.get();

                /* #2 - SEARCH FROM ALSONG SERVER */
                String lyrics = this.manager.getLyrics(hash, message);

                /* #3 - REPLY RESULT AND DELETE FILE */
                ALyricBot.reply(message, lyrics == null ? "❌ There are no lyrics for this music :(" : lyrics);
            }catch(Throwable e){
                this.reply(message, e);
            }
        }).start();
    }

    public static void reply(Message message, String content){
        try{
            Field messageIdField = Message.class.getDeclaredField("messageID");
            messageIdField.setAccessible(true);
            Integer messageId = (Integer) messageIdField.get(message);

            BotRequest request = new BotRequest(new TextMessage(message.isFromGroupChat() ? message.getGroupChat().getId() : message.getSender().getId(), content));
            request.getContent().addTextBody("reply_to_message_id", Integer.toString(messageId), ALyricBot.CONTENT_TYPE);

            Sender.bot.post(request);
        }catch(Throwable e){
            e.printStackTrace();
        }
    }

    public void reply(Message message, Throwable e){
        ALyricBot.reply(message, "⁉️ ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage().replaceAll("https?://\\S*", "[DATA EXPUNGED]"));
        e.printStackTrace();
    }
}
