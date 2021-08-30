package de.saar.minecraft.broker;

class Question {
    public final String question;
    public final QType type;

    public Question(String qstring) {
        var res = qstring.split(":", 2);
        if (res.length != 2) {
            throw new RuntimeException("Invalid question format: " + qstring
                + "\n" + "Expected: TYPE:QUESTION");
        }
        this.type = QType.valueOf(res[0]);
        this.question = res[1];
    }

    @Override
    public String toString() {
        return type.name() + ":" + question;
    }
}
