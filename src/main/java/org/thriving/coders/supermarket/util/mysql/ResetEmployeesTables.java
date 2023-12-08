package org.thriving.coders.supermarket.util.mysql;

import com.github.javafaker.Faker;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Locale;

/**
 * All tables related to employees are filled with NEW random data
 */
@Slf4j
public class ResetEmployeesTables {

    private static final String LANGUAGE = "ru";
    private static final int EMPLOYEES_COUNT = 20;//0;
    private static final int EMPLOYEES_ASSESSMENTS_COUNT = 1;//0;
    private static final int EMPLOYEES_SCHEDULES_DAYS_BACK = 5; // Number of days ago for schedules generation
    private static final int WORK_START = 5; // Start of work at 5 AM o clock

    public static void main(String[] args) {

        int startHour = WORK_START;// the employee's starting hour
        int startMinute = 30; // minutes of the employee's start time
        int workPreBreakInHour = 4; // duration of work before break in hours
        int breakDuration = 30; // Duration of the break in minutes
        int workAfterBreakDuration = 240; // Duration of work after a break in minutes

        Faker faker = new Faker(Locale.forLanguageTag(LANGUAGE));

        Connection connection;
        try { // edit connection string, if the database user is different
            connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/supermarket", "supermarket", "6zv7ss@QfeI37jTz");
        } catch (SQLException e) {
            log.error("Not connected to database: {}", e.getSQLState());
            throw new RuntimeException(e);
        }

        try {
            Statement statement = connection.createStatement();

            String deleteFromTable = "DELETE FROM %s;";
            // Delete from employees tables
            for (String employeesTable : EMPLOYEES_TABLES) {
                String query = String.format(deleteFromTable, employeesTable);
                statement.execute(query);
                log.info("All data deleted from table: {}", employeesTable);
            }

            for (int i = 0; i < EMPLOYEES_COUNT; i++) {
                // Filling the table employees
                String firstName = faker.name().firstName();
                String lastName = faker.name().lastName();
                String position = faker.job().position();
                int department = faker.number().numberBetween(1, 5);
                String contactInfo = faker.phoneNumber().phoneNumber();
                int hourlyRate = faker.number().numberBetween(10, 50);
                String commentary = faker.funnyName().name();

                String insertEmployeeQuery = String.format("INSERT INTO employees (firstName, lastName, position, department, contactInfo, hourlyRate, commentary) VALUES ('%s', '%s', '%s', %d, '%s', %d, '%s')",
                        firstName, lastName, position, department, contactInfo, hourlyRate, commentary);
                try {
                    statement.executeUpdate(insertEmployeeQuery, Statement.RETURN_GENERATED_KEYS);
                } catch (SQLException e) {
                    log.error("SQL error: {}", e.getMessage());
                }

                // Get the generated employeeId for use in other tables
                int employeeId;
                try (var resultSet = statement.getGeneratedKeys()) {
                    if (resultSet.next()) {
                        employeeId = resultSet.getInt(1);
                        log.info("Added employee with id: {}", employeeId);

                        // Filling the table employees_assessments
                        // Generate a random number of months to subtract from the current date
                        Date currentDate = new Date();
                        LocalDate localCurrentDate = currentDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

                        for (int j = 0; j < EMPLOYEES_ASSESSMENTS_COUNT; j++) {

                            // Generate date within up to - 2 months from the current date
                            int monthsToAddOrSubtract = faker.number().numberBetween(0, -2);

                            LocalDate generatedDate = localCurrentDate.plusMonths(monthsToAddOrSubtract);
                            Date assessmentDate = Date.from(generatedDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

                            // Convert date to MySQL format
                            String formattedDate = formatDate(assessmentDate, "yyyy-MM-dd");

                            int performanceRating = faker.number().numberBetween(1, 5);
                            int salesAnalysis = faker.number().numberBetween(100, 500);

                            // Filling the table employees_assessments with correct date
                            String insertAssessmentQuery = String.format("INSERT INTO employees_assessments (employeeId, assessmentDate, performanceRating, salesAnalysis) VALUES (%d, '%s', %d, %d)",
                                    employeeId, formattedDate, performanceRating, salesAnalysis);
                            statement.executeUpdate(insertAssessmentQuery);
                            log.info("Added employee  with id: {} to employees_assessments", employeeId);
                        }

                        // Filling the table employees_schedules
                        for (int j = EMPLOYEES_SCHEDULES_DAYS_BACK; j > 0; j--) {
                            LocalDate backDaysDate = LocalDate.now().minusDays(j);
                            // Check if the day is Sunday
                            if (backDaysDate.getDayOfWeek().getValue() == 7) continue; // we don't work on Sundays

                            // Logic for calculating the start and end times
                            // Initialization of working time before break
                            Date workStart = Date.from(backDaysDate.atTime(startHour, startMinute).atZone(ZoneId.systemDefault()).toInstant());
                            Date workPreBreakEnd = Date.from(backDaysDate.atTime(startHour + workPreBreakInHour, startMinute).atZone(ZoneId.systemDefault()).toInstant());
                            String insertPreBreakWorkQuery = String.format("INSERT INTO employees_schedules (employeeId, shiftDate, shiftType, workStart, workEnd) VALUES (%d, '%s', %d, '%s', '%s')",
                                    employeeId, formatDate(workStart, "yyyy-MM-dd"), SHIFT_TYPE.WORK_BEFORE_BREAK.getValue(), formatDate(workStart), formatDate(workPreBreakEnd));
                            statement.executeUpdate(insertPreBreakWorkQuery);

                            // Initialization of break start and end time
                            Date breakStart = Date.from(workPreBreakEnd.toInstant().plusSeconds(60 * breakDuration));
                            Date breakEnd = Date.from(breakStart.toInstant().plusSeconds(60 * breakDuration));
                            String insertBreakQuery = String.format("INSERT INTO employees_schedules (employeeId, shiftDate, shiftType, workStart, workEnd) VALUES (%d, '%s', %d, '%s', '%s')",
                                    employeeId, formatDate(breakStart, "yyyy-MM-dd"), SHIFT_TYPE.BREAK.getValue(), formatDate(breakStart), formatDate(breakEnd));
                            statement.executeUpdate(insertBreakQuery);

                            // Initialization of start time after break and end of work
                            Date workAfterBreakStart = Date.from(breakEnd.toInstant().plusSeconds(60 * breakDuration));
                            Date workEnd = Date.from(workAfterBreakStart.toInstant().plusSeconds(60 * workAfterBreakDuration));
                            String insertAfterBreakWorkQuery = String.format("INSERT INTO employees_schedules (employeeId, shiftDate, shiftType, workStart, workEnd) VALUES (%d, '%s', %d, '%s', '%s')",
                                    employeeId, formatDate(workAfterBreakStart, "yyyy-MM-dd"), SHIFT_TYPE.WORK_AFTER_BREAK.getValue(), formatDate(workAfterBreakStart), formatDate(workEnd));
                            statement.executeUpdate(insertAfterBreakWorkQuery);

                            log.info("Added employee with id: {} to employees_schedules for day: {}",employeeId, backDaysDate );
                        }
                        // Обновление времени начала работы для следующего сотрудника
                        startHour++;
                        if (startHour > 13) {
                            // employees don't work nights
                            startHour = WORK_START;
                        }


                    }



                } catch (SQLException e) {
                    log.error("SQL exception: " + e.getMessage());
                    throw new RuntimeException(e);
                }
            }

            statement.close();
            connection.close();
        } catch (SQLException e) {
            log.error("SQL exception: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }


    private static final String[] EMPLOYEES_TABLES = {
            "`supermarket`.`employees_assessments`",
            "`supermarket`.`employees_schedules`",
            "`supermarket`.`employees_sicks`",
            "`supermarket`.`employees_vacations`",
            "`supermarket`.`employees`"
    }; // always the last one to be deleted

    // Convert date to a string in MySQL format "yyyy-MM-dd HH:mm:ss" || "yyyy-MM-dd"
    private static String formatDate(Date date, String pattern) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(pattern);
        return dateFormat.format(date);
    }

    private static String formatDate(Date date) {
        return formatDate(date, "yyyy-MM-dd HH:mm");
    }

    private enum SHIFT_TYPE {
        WORK_BEFORE_BREAK(1), BREAK(2), WORK_AFTER_BREAK(3);
        private final int value;

        SHIFT_TYPE(int value) {
            this.value = value;
        }

        private int getValue() {
            return value;
        }
    }

}
