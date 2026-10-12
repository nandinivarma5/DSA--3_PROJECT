import java.time.LocalDate;
import java.util.Random;

/** Generates a deterministic demo dataset: 14 days of simulated news / social posts. */
public class SampleData {

    static final String[] BASE = {
        // Technology
        "The new smartphone launch is amazing, the camera and battery are excellent.",
        "Great update! The app feels faster and the new software is really smooth.",
        "Another data breach hits a cloud platform, users are angry and worried about hackers.",
        "Startup unveils an innovative robot that could improve warehouse safety.",
        "My laptop keeps crashing after the update, terrible software.",
        "Tech company announces a chip factory expansion this quarter.",
        "The internet outage today was a mess, slow connections everywhere.",
        "Developers are testing a new cloud tool for the platform.",
        // Business
        "Stock market rallies as investors cheer strong company earnings.",
        "Inflation is hurting customers, prices are expensive and sales are weak.",
        "The bank reports quarterly revenue in line with analysts' forecast.",
        "Retail brand announces layoffs as demand declines, a disappointing result.",
        "Small business growth is impressive this year, great news for the economy.",
        "Consumers love the affordable new product line, sales are up.",
        "Oil supply delays push up prices again, investors are worried.",
        // Politics
        "Parliament passes the budget bill after a long debate.",
        "Voters are angry about the corrupt campaign, protests continue in the capital.",
        "The minister welcomed the new policy, calling it a great step forward.",
        "Election commission announces the voting schedule for next month.",
        "Senate vote on the tax law delayed again, frustrating lawmakers.",
        "Opposition party criticizes the government policy as unfair.",
        // Health
        "New vaccine trial shows promising results, doctors are optimistic.",
        "Hospital staff shortage is a serious problem, patients face long delays.",
        "Health ministry releases guidelines for seasonal flu.",
        "Daily fitness and a healthy diet help improve mental wellness, experts say.",
        "Rising cancer cases worry doctors, a dangerous trend.",
        "Clinic opens a new treatment center for patients.",
        // Sports
        "What a fantastic match! The team played brilliant football and won the final.",
        "Terrible defeat for the home team, fans are angry at the coach.",
        "The league announces the tournament schedule for the new season.",
        "Champion player delivers an amazing goal, the stadium celebrated.",
        "Injury ruins the season for the star player, sad news.",
        "Cricket series begins Friday with two matches.",
        // Entertainment
        "The new movie is wonderful, brilliant acting and a great soundtrack.",
        "Disappointing series finale, the story was a mess.",
        "Streaming platform announces the trailer date for the upcoming film.",
        "Concert was awesome, I loved every song.",
        "Music festival cancelled, fans are sad and angry.",
        "Director confirms a new album release at the award show.",
        // Environment
        "Heatwave and drought worry farmers, a serious threat to crops.",
        "Solar energy projects see great progress, a positive step for the climate.",
        "Pollution levels are dangerous this week, residents fear for their lungs.",
        "Weather department forecasts rain and storm warnings for the coast.",
        "Forest conservation drive celebrated, wonderful news for wildlife.",
        "Flood damage is terrible, the city is in chaos.",
        // Education
        "University announces a new scholarship, students are thrilled.",
        "Exam results delayed again, students are frustrated and angry.",
        "College schedules the admission process for the next semester.",
        "Teachers praise the new curriculum, an excellent move for learning.",
        "Online course platform adds new classes for students."
    };

    // A story that builds up over the last week -> appears as an "emerging discussion"
    static final String[] EMERGING = {
        "Lawmakers propose new AI regulation after deepfake scandal spreads online.",
        "Deepfake videos are dangerous, voters fear misinformation before the election.",
        "Experts welcome the AI regulation bill as a great step for safe technology.",
        "Tech firms say strict AI regulation could hurt innovation and startups.",
        "Public support for AI regulation grows, many hope deepfake rules protect people.",
        "Government to hold hearing on AI regulation and deepfake detection tools.",
        "Deepfake of a minister goes viral, a terrible threat to trust.",
        "Researchers build an innovative deepfake detection tool, impressive progress."
    };

    static final String[] SOURCES = {"News", "Twitter/X", "Reddit", "Facebook", "Blog comments", "News"};

    public static String sampleText() {
        Random rnd = new Random(35);                         // Team 35
        LocalDate start = LocalDate.of(2026, 9, 28);
        StringBuilder sb = new StringBuilder();
        for (int d = 0; d < 14; d++) {
            LocalDate day = start.plusDays(d);
            int count = 7 + rnd.nextInt(5);                  // 7..11 posts per day
            for (int k = 0; k < count; k++) {
                double pEmerge = d < 5 ? 0.0 : 0.55 * Math.pow((d - 4) / 9.0, 1.2);
                String[] pool = rnd.nextDouble() < pEmerge ? EMERGING : BASE;
                String text = pool[rnd.nextInt(pool.length)];
                String source = SOURCES[rnd.nextInt(SOURCES.length)];
                sb.append(day).append(" | ").append(source).append(" | ").append(text).append('\n');
            }
        }
        return sb.toString();
    }
}
