import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;

public class GoBabbyApp{
    public static void main(String args[]) throws SQLException {
        //database set up
        Connection con = databaseSetup();
        Statement statement = con.createStatement();

        int errCode;

        while(true) {
            // enter practitioner id, errCodes: 2, 1, 0
            if ((errCode = promptPractid(statement)) != 0) {
                if (errCode == 1) continue; // re-enter practid
                if (errCode == 2) break; //exit
            }

            ArrayList<String> aptlist = new ArrayList<>();

            while (true) {
                // enter date for appointment, errCodes: 3, 2, 0
                if ((errCode = promptAptDate(statement, aptlist)) != 0) {
                    if (errCode == 3) continue; // re-enter apt date
                    break; // err = 2 -> exit
                }

                while (true) {
                    // choose appointment number, errCodes: 3, 2, 0
                    if ((errCode = promptAptNum(statement, aptlist)) != 0) break;

                    // choose action to take for the apt, errCodes: 0, 5
                    while ((errCode = promptAptAction(statement, aptlist)) != 5);

                    // re-choose apt number
                }
                if (errCode == 2) break; // exit
                // otherwise errCode == 3, re-enter appointment date
            }
            if (errCode == 2) break; //exit
            // errCode == 1, re-enter practid
        }

        // last step to all: close the statement and connection
        System.out.println("Exit the application");
        statement.close();
        con.close();
    }

    /* helper functions */
    public static Connection databaseSetup() throws SQLException {
        // set up database
        // Register the driver.  You must register the driver before you can use it.
        try {
            DriverManager.registerDriver ( new com.ibm.db2.jcc.DB2Driver() ) ;
        } catch (Exception cnfe){
            System.out.println("Class not found");
        }

        String url = "jdbc:db2://winter2022-comp421.cs.mcgill.ca:50000/cs421";

        //TODO: remove id & password for submission
        //$  export SOCSPASSWD=yoursocspasswd
        String your_userid = "ypeng25";
        String your_password = "Gewuzhizhi";
        if(your_userid == null && (your_userid = System.getenv("SOCSUSER")) == null) {
            System.err.println("Error!! do not have a password to connect to the database!");
            System.exit(1);
        }
        if(your_password == null && (your_password = System.getenv("SOCSPASSWD")) == null) {
            System.err.println("Error!! do not have a password to connect to the database!");
            System.exit(1);
        }
        Connection con = DriverManager.getConnection (url,your_userid,your_password) ;
        return con;
    }
    public static void printError(SQLException e) {
        int sqlCode = e.getErrorCode(); // Get SQLCODE
        String sqlState = e.getSQLState(); // Get SQLSTATE
        System.out.println("Code: " + sqlCode + "  sqlState: " + sqlState);
        System.out.println(e);
    }
    public static boolean validateDate(String strDate) {
        /* Check if date is 'null' */
        if (strDate.trim().equals("")) return false;

        /* Date is not 'null' */
        else {
            SimpleDateFormat sdfrmt = new SimpleDateFormat("yyyy-MM-dd");
            sdfrmt.setLenient(false);

            try {
                sdfrmt.parse(strDate);
                return true;
            }
            catch (ParseException e) { return false; }
        }
    }

    /* prompts */
    public static int promptPractid(Statement statement) {
        System.out.println("Please enter your practitioner id [E] to exit:");

        Scanner scanner = new Scanner(System.in);
        String in_pid = scanner.nextLine();

        if (in_pid.equals("E")) return 2;

        String sqlString = String.format("SELECT * FROM Midwife WHERE practid = \'%s\'", in_pid);
        try {
            ResultSet rs = statement.executeQuery(sqlString);
            if (!rs.next()) {
                System.out.println("# Error! practitioner id does not exist\n");
                return 1;
            }
            rs.close();
        }
        catch (SQLException e) {
            printError(e);
            return 1;
        }

        return 0;
    }
    public static int promptAptDate(Statement statement, ArrayList<String> aptlist) {
        Scanner scanner = new Scanner(System.in);
        boolean hasRecord = false; // flag for whether appointment exists for the given date
        aptlist.clear();
        aptlist.add("");

        System.out.println("Please enter the date for appointment list [E] to exit:");

        String in_date = scanner.nextLine();
        if (in_date.equals("D")) return 3;
        if (in_date.equals("E")) return 2;

        if (!validateDate(in_date)) {
            System.out.println("# Error! Date is not well formatted, please re-enter\n");
            return 3;
        }

        String whereStr = String.format("WHERE adate = \'%s\'\n", in_date);
        String sqlString = "WITH pregapt AS (" +
                "SELECT primarypid, backuppid, aid, adate, atime, m.practid, m.name, p.cid, p.num\n" +
                "FROM Pregnancy p JOIN Appointment a ON (p.cid, p.num) = (a.cid, a.pregnum)\n" +
                "JOIN Midwife m ON m.practid = a.practid),\n" +
                "mompreg AS (\n" +
                "SELECT m.hcid, mname, p.cid, p.num\n" +
                "FROM Couple c JOIN Pregnancy p ON c.cid = p.cid\n" +
                "JOIN Mother m ON m.hcid = c.hcid)\n" +
                "SELECT aid, adate, atime,\n" +
                "CASE\n" +
                "WHEN practid = primarypid THEN 'P'\n" +
                "ELSE 'B'\n" +
                "END AS role, mname, hcid, a.cid, a.num, a.practid\n" +
                "FROM pregapt a JOIN mompreg m ON (a.cid, a.num) = (m.cid, m.num)\n" +
                whereStr +
                "ORDER BY atime";
        try {
            ResultSet rs = statement.executeQuery(sqlString);
            while (rs.next()) {
                hasRecord = true;
                String aid = rs.getString("aid");
                Time atime = rs.getTime("atime");
                String role = rs.getString("role");
                String mname = rs.getString("mname");
                String hcid = rs.getString("hcid");
                String cid = rs.getString("cid");
                String practid = rs.getString("practid");
                int pregnum = rs.getInt("num");
                aptlist.add(aid +","+ atime +","+ role +","
                        + mname +","+ hcid +","+ cid +","+ pregnum +","+ practid);
            }
            rs.close();
            if (!hasRecord) {
                System.out.println("# Error! No appointment found for the given date\n");
                return 3;
            }
        }
        catch (SQLException e) {
            printError(e);
            return 1;
        }

        return 0;
    }
    public static int promptAptNum(Statement statement, ArrayList<String> aptlist) {
        for (int i = 1; i < aptlist.size(); i++) {
            String[] splits = aptlist.get(i).split(",");
            System.out.println(i + ":  " + splits[1].substring(0, 5) +" "+ splits[2] +" "+ splits[3] +" "+ splits[4]);
        }
        System.out.println("\nEnter the appointment number that you would like to work on.");
        System.out.println("\t[E] to exit [D] to go back to another date");

        Scanner scanner = new Scanner(System.in);
        String in_numstr;

        while (true) {
            in_numstr = scanner.nextLine();

            if (in_numstr.equals("D")) return 3;
            if (in_numstr.equals("E")) return 2;

            try {
                int in_num = Integer.parseInt(in_numstr);
                if (in_num > aptlist.size() - 1 || in_num < 1) {
                    System.out.println("# Error! Please enter one of the listed appointment numbers");
                    System.out.println("\t[E] to exit [D] to go back to another date");
                    continue;
                }
            }
            catch (NumberFormatException e) {
                System.out.println("# Error! Please enter a number\n");
                continue;
            }
            aptlist.set(0, in_numstr);
            break;
        }
        return 0;
    }
    public static int promptAptAction(Statement statement, ArrayList<String> aptlist) {
        Scanner scanner = new Scanner(System.in);

        int index = Integer.parseInt(aptlist.get(0));
        String[] apt = aptlist.get(index).split(",");

        System.out.println("For " + apt[3] + " " + apt[4] + "\n");
        System.out.println("1. Review notes");
        System.out.println("2. Review tests");
        System.out.println("3. Add a note");
        System.out.println("4. Prescribe a test");
        System.out.println("5. Go back to the appointments.\n");
        System.out.println("Enter your choice:");

        try {
            int in_choice = scanner.nextInt();
            switch (in_choice) {
                case 1:
                    reviewNotes(statement, apt);
                    return 0;
                case 2:
                    reviewTests(statement, apt);
                    return 0;
                case 3:
                    addNote(statement, apt);
                    return 0;
                case 4:
                    prescribeTest(statement, apt);
                    return 0;
                case 5: return 5;
                default:
                    System.out.println("# Error! Please enter one of the given choices\n");
            }
        }
        catch (InputMismatchException e) {
            System.out.println("# Error! Please enter a number\n");
        }

        return 0;
    }

    /* actions to take for a pregnancy/appointment */
    public static void reviewNotes(Statement statement, String[] record) {
        System.out.println("Reviewing notes");

        String cid = record[5];
        String pregnum = record[6];
        boolean hasEntry = false;

        String whereStr = "WHERE (a.cid, a.pregnum) = (\'" + cid +"\', \'"+ pregnum +"\')";
        String sqlString = "SELECT ndate, ntime, observation\n" +
                "FROM Appointment a JOIN Note n ON a.aid = n.aid\n" +
                whereStr +
                "ORDER BY ndate DESC, ntime DESC";

        try {
            ResultSet rs = statement.executeQuery(sqlString);
            while (rs.next()) {
                hasEntry = true;
                String date = rs.getString("ndate");
                String time = rs.getString("ntime");
                String observ = rs.getString("observation");
                observ = observ.substring(0, Math.min(observ.length(), 50));
                String msg = date +" "+ time +"  "+ observ;
                System.out.println(msg);
            }
            if (!hasEntry) System.out.println("# No notes found for this pregnancy");
            System.out.println();
            rs.close();
        }
        catch (SQLException e) {
            printError(e);
        }
    }
    public static void reviewTests(Statement statement, String[] record) {
        System.out.println("Reviewing tests for mother");

        String cid = record[5];
        String pregnum = record[6];
        boolean hasEntry = false;

        String whereStr = String.format("WHERE (cid, pregnum) = (\'%s\', \'%s\') AND babyid IS NULL\n", cid, pregnum);
        String sqlString = "SELECT dateprescribed, type, COALESCE(result, 'PENDING') AS result\n" +
                "FROM Test\n" +
                whereStr +
                "ORDER BY dateprescribed DESC";

        try {
            ResultSet rs = statement.executeQuery(sqlString);
            while (rs.next()) {
                hasEntry = true;
                String date = rs.getString("dateprescribed");
                String type = rs.getString("type");
                String result = rs.getString("result");
                result = result.substring(0, Math.min(result.length(), 50));
                String msg = String.format("%s [%s] %s", date, type, result);
                System.out.println(msg);
            }
            if (!hasEntry) System.out.println("# No test found for this pregnancy");
            System.out.println();
            rs.close();
        }
        catch (SQLException e) {
            printError(e);
        }
    }
    public static void addNote(Statement statement, String[] record) {
        System.out.println("Please type your observation:");

        Scanner scanner = new Scanner(System.in);
        String observation = scanner.nextLine();
        observation = observation.substring(0, Math.min(observation.length(), 300));

        String nid = "";
        String sqlQueryString = "SELECT MAX(nid) AS nid FROM Note";
        try {
            ResultSet rs = statement.executeQuery(sqlQueryString);
            if (rs.next()) {
                nid = rs.getString("nid");
            } else {
                nid = "note00";
            }
            rs.close();
        }
        catch (SQLException e) { printError(e); }

        int nid_num = Integer.parseInt(nid.substring(4));
        nid_num ++;
        nid = String.format("note%02d", nid_num);

        String aid = record[0];

        SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
        Date datetime = new Date();
        String ndate = dayFormat.format(datetime);
        String ntime = timeFormat.format(datetime);

        String sqlString = String.format("INSERT INTO Note (nid, aid, ndate, ntime, observation) VALUES " +
                        "('%s', '%s', '%s', '%s', '%s')", nid, aid, ndate, ntime, observation);

        try {
            statement.executeUpdate(sqlString);
        }
        catch (SQLException e) {
            printError(e);
        }
        System.out.println("+ Note successfully added\n");
    }
    public static void prescribeTest(Statement statement, String[] record) {
        System.out.println("Please enter your type of test:");

        // get type
        Scanner scanner  = new Scanner(System.in);
        String type = scanner.nextLine();
        type = type.substring(0, Math.min(type.length(), 50));

        // get tid
        String tid = "";
        String sqlQueryString = "SELECT MAX(tid) AS tid FROM Test";
        try {
            ResultSet rs = statement.executeQuery(sqlQueryString);
            if (rs.next()) {
                tid = rs.getString("tid");
            } else {
                tid = "test00";
            }
            rs.close();
        }
        catch (SQLException e) { printError(e); }

        int tid_num = Integer.parseInt(tid.substring(4));
        tid_num ++;
        tid = String.format("test%02d", tid_num);

        // get practid
        String practid = record[7];

        // get dateprescribed and datesampled = today
        SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date datetime = new Date();
        String date = dayFormat.format(datetime);

        // get cid and pregnum from record[]
        String cid = record[5];
        String pregnum = record[6];

        String sqlString = String.format("INSERT INTO Test (tid, practid, type, dateprescribed, datesampled, cid, pregnum) VALUES " +
                "('%s', '%s', '%s', '%s', '%s', '%s', '%s')", tid, practid, type, date, date, cid, pregnum);

        try {
            statement.executeUpdate(sqlString);
        }
        catch (SQLException e) {
            printError(e);
        }
        System.out.println("+ Test successfully added\n");
    }
}
