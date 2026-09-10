import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * vocab.tsv 의 예문을 형태소로 끊어 tokens.tsv 를 만든다.
 *
 * 이 프로그램은 빌드 도구다 — 형태소 분석기(kuromoji-ipadic, 12.7MB)는 APK에
 * 들어가지 않는다. 분석할 문장이 정해진 5,171개라 폰에서 분석할 것이 없다.
 * 예문을 고치거나 줄을 더하면 tools/tokens.sh 를 다시 돌린다.
 *
 * 나오는 줄: 표기 \t 토큰 토큰 토큰...
 * 토큰 한 개: 표면형:기본형:읽기:품사
 *   - 기본형·읽기가 표면형과 같으면 비워 둔다 (파일이 3분의 1로 줄어든다)
 *   - 품사는 내용어(名詞·動詞·形容詞·副詞)에만 적는다. 빈 것은 조사·조동사·기호다
 *   - 읽기는 가타카나를 히라가나로 옮겨 둔다. 화면이 후리가나로 쓰기 때문이다
 */
public class Tok {

    /** 내용어로 볼 품사. 이것만 눌러서 뜻을 볼 수 있게 한다. */
    private static boolean isContent(String pos) {
        return pos.equals("名詞") || pos.equals("動詞")
            || pos.equals("形容詞") || pos.equals("副詞");
    }

    private static String hiragana(String katakana) {
        if (katakana == null) return "";
        StringBuilder b = new StringBuilder();
        for (char c : katakana.toCharArray())
            b.append(c >= 'ァ' && c <= 'ヶ' ? (char) (c - 0x60) : c);
        return b.toString();
    }

    public static void main(String[] args) throws Exception {
        Tokenizer tokenizer = new Tokenizer();
        StringBuilder out = new StringBuilder();
        int sentences = 0, tokens = 0, content = 0, bad = 0;

        for (String line : Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            String[] f = line.split("\t");
            // 조용히 넘기면 그 단어만 tokens.tsv 에서 빠져 화면에서 예문이 사라진다.
            if (f.length < 6) {
                System.err.println("칸이 모자란 줄: " + f[0]);
                bad++;
                continue;
            }

            List<String> parts = new ArrayList<>();
            for (Token t : tokenizer.tokenize(f[5])) {
                String surface = t.getSurface();
                String pos = t.getPartOfSpeechLevel1();
                String base = t.getBaseForm() == null || t.getBaseForm().equals("*")
                    ? surface : t.getBaseForm();
                String read = hiragana(t.getReading());
                boolean isContent = isContent(pos);

                tokens++;
                if (isContent) content++;
                parts.add(surface
                    + ":" + (base.equals(surface) ? "" : base)
                    + ":" + (read.equals(surface) ? "" : read)
                    + ":" + (isContent ? pos : ""));
            }
            out.append(f[0]).append('\t').append(String.join(" ", parts)).append('\n');
            sentences++;
        }

        // 버린 줄이 있으면 쓰지 않고 나간다. 먼저 쓰면 그 단어가 빠진 tokens.tsv 가
        // 멀쩡한 파일을 덮어써서, 실패한 실행이 리소스를 망가뜨린 채로 남는다.
        if (bad > 0) {
            System.err.printf("버린 줄 %d개 — 쓰지 않는다. vocab.tsv 를 고치고 다시 돌린다%n", bad);
            System.exit(1);
        }

        Files.write(Path.of(args[1]), out.toString().getBytes(StandardCharsets.UTF_8));
        System.out.printf("문장 %d · 토큰 %d · 내용어 %d → %s%n",
            sentences, tokens, content, args[1]);
    }
}
