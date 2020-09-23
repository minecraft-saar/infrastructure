package de.saar.minecraft.broker;

import de.saar.minecraft.broker.db.tables.Questionnaires;
import de.saar.minecraft.shared.NewGameState;
import de.saar.minecraft.shared.TextMessage;
import java.util.List;
import java.util.Set;
import org.jooq.DSLContext;

/**
 * A Questionnaire is created once the player finishes their game.
 * It sends initial information and receives all text messages written by the player
 * from that point onwards.
 */
class Questionnaire {

    public final int gameId;
    public List<Question> questions;
    public final DelegatingStreamObserver stream;
    private int currQuestion = 0;
    private AnswerThread currThread;
    private DSLContext jooq;


    public Questionnaire(int gameId,
                         List<Question> questions,
                         DelegatingStreamObserver stream,
                         DSLContext jooq) {
        this.jooq = jooq;
        this.questions = questions;
        this.stream = stream;
        this.gameId = gameId;
    }

    public void start() {
        new Thread(() -> {
            try {
                Thread.sleep(4000);
                sendText("We would like you to answer a few questions.");
                sendText("You can answer them by pressing \"t\","
                    + " typing the answer and then pressing return.");
                sendText("Most questions should be answered with a number between "
                    + " 1 (completely disagree) and 5 (completely agree)");
                Thread.sleep(4000);
                sendNextQuestion();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * processes a text message sent by the player.
     * @param request The message object forwarded by the BrokerImpl.
     */
    public void onNext(TextMessage request) {
        var answer = request.getText();
        boolean answerIsValid = false;
        // for text messages after the questionnaire is finished
        if (currQuestion >= questions.size()) {
            sendText("Thank you for your time! you can hang around or disconnect now.");
            return;
        }
        switch (questions.get(currQuestion).type) {
            case FREE:
                answerIsValid = true;
                break;
            case LIKERT:
                answerIsValid = Set.of("1","2","3","4","5").contains(answer);
                break;
        }

        if (!answerIsValid) {
            sendText("please answer with a number between "
                + "1 (completely disagree) and 5 (completely agree)");
            return;
        }

        // Reset the timer which reminds the player to continue the questionnaire
        currThread.interrupt();

        var record = jooq.newRecord(Questionnaires.QUESTIONNAIRES);
        if (answer.length() > 4999) {
            answer = answer.substring(0, 4999);
        }
        record.setAnswer(answer);
        record.setGameid(gameId);
        record.setQuestion(questions.get(currQuestion).toString());
        record.setTimestamp(Broker.now());
        record.store();

        currQuestion += 1;
        if (currQuestion == questions.size()) {
            stream.onNext(TextMessage.newBuilder()
                .setGameId(gameId)
                .setText("Thank you for your time! you can hang around or disconnect now.")
                .setNewGameState(NewGameState.QuestionnaireFinished)
                .build());
        } else {
            sendNextQuestion();
        }
    }

    /**
     * A Thread that sends a reminder to the player to continue with the questionnaire.
     * The timer is reset by interrupting the thread.
     */
    class AnswerThread extends Thread {
        public void run() {
            while (true) {  // TODO: is there a maximum of attempts?
                sendText(questions.get(currQuestion).question);
                try {
                    Thread.sleep(30000);
                } catch (InterruptedException e) {
                    break;
                }
                sendText("You can answer them by pressing \"t\", "
                    + "typing the answer and then pressing return.");
            }
        }
    }

    private void sendNextQuestion() {
        currThread = new AnswerThread();
        currThread.start();
    }

    private synchronized void sendText(String text) {
        stream.onNext(TextMessage.newBuilder()
            .setGameId(gameId)
            .setText(text)
            .build());
    }
}
