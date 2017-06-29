package ledge.muscleup.persistence;

import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import ledge.muscleup.model.exercise.Exercise;
import ledge.muscleup.model.exercise.ExerciseDistance;
import ledge.muscleup.model.exercise.ExerciseDuration;
import ledge.muscleup.model.exercise.ExerciseSets;
import ledge.muscleup.model.exercise.ExerciseSetsAndWeight;
import ledge.muscleup.model.exercise.InterfaceExerciseQuantity;
import ledge.muscleup.model.exercise.WorkoutExercise;
import ledge.muscleup.model.exercise.WorkoutExerciseDistance;
import ledge.muscleup.model.exercise.WorkoutExerciseDuration;
import ledge.muscleup.model.exercise.WorkoutExerciseSets;
import ledge.muscleup.model.exercise.WorkoutExerciseSetsAndWeight;
import ledge.muscleup.model.exercise.WorkoutSessionExercise;
import ledge.muscleup.model.exercise.enums.DistanceUnit;
import ledge.muscleup.model.exercise.enums.ExerciseIntensity;
import ledge.muscleup.model.exercise.enums.ExerciseType;
import ledge.muscleup.model.exercise.enums.TimeUnit;
import ledge.muscleup.model.exercise.enums.WeightUnit;
import ledge.muscleup.model.schedule.ScheduleWeek;
import ledge.muscleup.model.workout.Workout;
import ledge.muscleup.model.workout.WorkoutSession;

/**
 * This is it. THE REAL DEAL. ARE YOU READY?!?!?!?!?
 *
 * @author Cole Kehler
 * @version 2.0
 * @since 2017-06-27
 */

public class DataAccess implements InterfaceExerciseDataAccess, InterfaceWorkoutDataAccess, InterfaceWorkoutSessionDataAccess {
    private static final String SHUTDOWN_CMD = "shutdown compact";
    private static final DateTimeFormatter format = DateTimeFormat.forPattern("yyyy-MM-dd");
    private static final int NULL_NUM = -1;
    private static final int XP_PER_INTENSITY = 10;

    private String dbName;
    private String dbType = "HSQLDB";
    private String dbPathPrefix = "jdbc:hsqldb:file:";

    private Connection connection;
    private Statement statement;
    private ResultSet resultSet, resultSet2;

    /**
     * Constructor for DataAccess
     *
     * @param dbName the name of the database
     */
    public DataAccess (String dbName) {
        this.dbName = dbName;
    }

    /**
     * Opens the database
     */
    @Override
    public void open(String dbPath) {
        try {
            Class.forName("org.hsqldb.jdbcDriver").newInstance();
            connection = DriverManager.getConnection(dbPathPrefix + dbPath, "SA", "");
            statement = connection.createStatement();
        }
        catch (Exception e) {
            sqlError(e);
        }
        System.out.println("Opened " + dbType + " database " + dbName);
    }

    /**
     * Close the database
     */
    @Override
    public void close() {
        try {
            resultSet = statement.executeQuery(SHUTDOWN_CMD);
            connection.close();
        }
        catch (Exception e) {
            sqlError(e);
        }
        System.out.println("Closed " + dbType + " database " + dbName);
    }

    /**
     * Gets a list of all exercises in the database
     *
     * @return a list of all exercises in the database
     */
    @Override
    public List<Exercise> getExercisesList() {
        List<Exercise> exerciseList = new ArrayList<>();
        String name;
        ExerciseIntensity intensity;
        ExerciseType type;
        boolean favourite;

        try {
            resultSet = statement.executeQuery(
                    "SELECT     E.Name, " +
                    "           EI.Intensity, " +
                    "           ET.Type, " +
                    "           E.Favourite " +
                    "FROM       Exercises E " +
                    "LEFT JOIN  ExerciseIntensities EI " +
                    "           ON E.IntensityID = EI.ID " +
                    "LEFT JOIN  ExerciseTypes ET " +
                    "           ON E.TypeID = ET.ID");

            while (resultSet.next()) {
                name = resultSet.getString("Name");
                intensity = ExerciseIntensity.valueOf(resultSet.getString("Intensity"));
                type = ExerciseType.valueOf(resultSet.getString("Type"));
                favourite = resultSet.getBoolean("Favourite");

                exerciseList.add(new Exercise(name, intensity, type, favourite));
            }
            resultSet.close();
        }
        catch (Exception e) {
            sqlError(e);
        }

        return exerciseList;
    }

    /**
     * Retrieves a workout session scheduled on the given date from the database, if it exists. If
     * no workout session is found for that date, returns null.
     *
     * @param dateOfSession the date to get the workout session for
     * @return the workout session scheduled on the given date
     */
    @Override
    public WorkoutSession getWorkoutSession(LocalDate dateOfSession) {
        ArrayList<WorkoutSessionExercise> workoutSessionExerciseList = new ArrayList<>();
        Exercise exercise;
        WorkoutExercise workoutExercise;
        WorkoutSession workoutSession = null;

        String workoutName = null, exerciseName;
        int xpValue, duration, sets, reps;
        double distance, weight;
        DistanceUnit distanceUnit;
        TimeUnit timeUnit;
        WeightUnit weightUnit;
        LocalDate scheduledDate = null;
        ExerciseIntensity intensity;
        ExerciseType type;
        boolean exerciseFavourite, workoutComplete = false, exerciseComplete;

        try {
            resultSet = statement.executeQuery(
                    "SELECT		W.Name AS WorkoutName, " +
                    "			WS.ScheduleDate, " +
                    "			WS.Complete AS WorkoutComplete, " +
                    "			E.Name AS ExerciseName, " +
                    "			EI.Intensity, " +
                    "			ET.Type, " +
                    "			E.Favourite, " +
                    "			WE.Distance, " +
                    "			DiU.DistanceUnit, " +
                    "			WE.Duration, " +
                    "			DuU.DurationUnit, " +
                    "			WE.Sets, " +
                    "			WE.Reps, " +
                    "			WE.Weight, " +
                    "			WU.WeightUnit,  " +
                    "			WSE.Complete AS ExerciseComplete " +
                    "FROM		WorkoutSessions WS " +
                    "LEFT JOIN	Workouts W " +
                    "			ON WS.WorkoutID = W.ID " +
                    "LEFT JOIN	WorkoutSessionContents WSC " +
                    "			ON WSC.WorkoutSessionID = WS.ID " +
                    "LEFT JOIN	WorkoutSessionExercises WSE " +
                    "			ON WSC.ExerciseID = WSE.ID " +
                    "LEFT JOIN	WorkoutExercises WE " +
                    "			ON WSE.WorkoutExerciseID = WE.ID " +
                    "LEFT JOIN	DistanceUnits DiU " +
                    "			ON WE.DistanceUnitID = DiU.ID " +
                    "LEFT JOIN	DurationUnits DuU " +
                    "			ON WE.DurationUnitID = DuU.ID " +
                    "LEFT JOIN	WeightUnits WU " +
                    "			ON WE.WeightUnitID = WU.ID " +
                    "LEFT JOIN	Exercises E " +
                    "			ON WE.ExerciseID = E.ID " +
                    "LEFT JOIN  ExerciseIntensities EI " +
                    "           ON E.IntensityID = EI.ID  " +
                    "LEFT JOIN  ExerciseTypes ET " +
                    "           ON E.TypeID = ET.ID " +
                    "WHERE		WS.ScheduleDate = " + format.print(dateOfSession));

            while (resultSet.next()) {
                if (workoutName == null) {
                    workoutName = resultSet.getString("WorkoutName");
                    scheduledDate = new LocalDate(resultSet.getDate("ScheduleDate"));
                    workoutComplete = resultSet.getBoolean("WorkoutComplete");
                }

                exerciseName = resultSet.getString("ExerciseName");
                intensity = ExerciseIntensity.valueOf(resultSet.getString("Intensity"));
                type = ExerciseType.valueOf(resultSet.getString("Type"));
                exerciseFavourite = resultSet.getBoolean("Favourite");
                exercise = new Exercise(exerciseName, intensity, type, exerciseFavourite);

                xpValue = XP_PER_INTENSITY * ExerciseIntensity.valueOf(resultSet.getString("Intensity")).ordinal();
                distance = resultSet.getDouble("Distance");
                distanceUnit = DistanceUnit.valueOf(resultSet.getString("DistanceUnit"));
                duration = resultSet.getInt("Duration");
                timeUnit = TimeUnit.valueOf(resultSet.getString("DurationUnit"));
                sets = resultSet.getInt("Sets");
                reps = resultSet.getInt("Reps");
                weight = resultSet.getDouble("Weight");
                weightUnit = WeightUnit.valueOf(resultSet.getString("WeightUnit"));
                workoutExercise = createWorkoutExercise(exercise, xpValue, distance, distanceUnit, duration,
                        timeUnit, sets, reps, weight, weightUnit);

                exerciseComplete = resultSet.getBoolean("ExerciseCompleted");
                workoutSessionExerciseList.add(new WorkoutSessionExercise(workoutExercise, exerciseComplete));
            }

            if (workoutName != null)
                workoutSession = new WorkoutSession(workoutName, scheduledDate, workoutComplete, workoutSessionExerciseList);

            resultSet.close();
        }
        catch (Exception e) {
            sqlError(e);
        }

        return workoutSession;
    }

    /**
     * A method that returns a list of workout sessions scheduled in a date range
     *
     * @param startDate the first date of the date range
     * @param endDate   the last date of the date range
     * @return a list of all workout sessions scheduled between startDate and endDate, inclusive
     */
    @Override
    public List<WorkoutSession> getSessionsInDateRange(LocalDate startDate, LocalDate endDate) {
        ArrayList<WorkoutSession> workoutSessionList = new ArrayList<>();
        ArrayList<WorkoutSessionExercise> workoutSessionExerciseList = new ArrayList<>();
        Exercise exercise;
        WorkoutExercise workoutExercise;
        WorkoutSession workoutSession;

        String workoutName = null, exerciseName;
        int xpValue, duration, sets, reps;
        double distance, weight;
        DistanceUnit distanceUnit;
        TimeUnit timeUnit;
        WeightUnit weightUnit;
        LocalDate scheduledDate = null;
        ExerciseIntensity intensity;
        ExerciseType type;
        boolean exerciseFavourite, workoutComplete = false, exerciseComplete;

        try {
            resultSet = statement.executeQuery(
                    "SELECT		W.Name AS WorkoutName, " +
                    "			WS.ScheduledDate, " +
                    "			WS.Complete AS WorkoutComplete, " +
                    "			E.Name AS ExerciseName, " +
                    "			EI.Intensity, " +
                    "			ET.Type, " +
                    "			E.Favourite, " +
                    "			WE.Distance, " +
                    "			DiU.DistanceUnit, " +
                    "			WE.Duration, " +
                    "			DuU.DurationUnit, " +
                    "			WE.Sets, " +
                    "			WE.Reps, " +
                    "			WE.Weight, " +
                    "			WU.WeightUnit,  " +
                    "			WSE.Complete AS ExerciseComplete " +
                    "FROM		WorkoutSessions WS " +
                    "LEFT JOIN	Workouts W " +
                    "			ON WS.WorkoutID = W.ID " +
                    "LEFT JOIN	WorkoutSessionContents WSC " +
                    "			ON WSC.WorkoutSessionID = WS.ID " +
                    "LEFT JOIN	WorkoutSessionExercises WSE " +
                    "			ON WSC.ExerciseID = WSE.ID " +
                    "LEFT JOIN	WorkoutExercises WE " +
                    "			ON WSE.WorkoutExerciseID = WE.ID " +
                    "LEFT JOIN	DistanceUnits DiU " +
                    "			ON WE.DistanceUnitID = DiU.ID " +
                    "LEFT JOIN	DurationUnits DuU " +
                    "			ON WE.DurationUnitID = DuU.ID " +
                    "LEFT JOIN	WeightUnits WU " +
                    "			ON WE.WeightUnitID = WU.ID " +
                    "LEFT JOIN	Exercises E " +
                    "			ON WE.ExerciseID = E.ID " +
                    "LEFT JOIN  ExerciseIntensities EI " +
                    "           ON E.IntensityID = EI.ID  " +
                    "LEFT JOIN  ExerciseTypes ET " +
                    "           ON E.TypeID = ET.ID " +
                    "WHERE		DATEDIFF('day', WS.ScheduledDate, DATE'" + format.print(startDate) + "') <= 0" +
                    "           AND DATEDIFF('day', WS.ScheduledDate, DATE'" + format.print(endDate) + "') >= 0");

            while (resultSet.next()) {
                if (workoutName == null) {
                    workoutName = resultSet.getString("WorkoutName");
                    scheduledDate = new LocalDate(resultSet.getDate("ScheduledDate"));
                    workoutComplete = resultSet.getBoolean("WorkoutComplete");
                }
                else if (!workoutName.equals(resultSet.getString("WorkoutName"))) {
                    workoutSession = new WorkoutSession(workoutName, scheduledDate, workoutComplete, workoutSessionExerciseList);
                    workoutSessionList.add(workoutSession);

                    workoutName = resultSet.getString("WorkoutName");
                    scheduledDate = new LocalDate(resultSet.getDate("ScheduleDate"));
                    workoutComplete = resultSet.getBoolean("WorkoutComplete");
                }

                exerciseName = resultSet.getString("ExerciseName");
                intensity = ExerciseIntensity.valueOf(resultSet.getString("Intensity"));
                type = ExerciseType.valueOf(resultSet.getString("Type"));
                exerciseFavourite = resultSet.getBoolean("Favourite");
                exercise = new Exercise(exerciseName, intensity, type, exerciseFavourite);

                xpValue = XP_PER_INTENSITY * ExerciseIntensity.valueOf(resultSet.getString("Intensity")).ordinal();
                distance = resultSet.getDouble("Distance");
                distanceUnit = DistanceUnit.valueOf(resultSet.getString("DistanceUnit"));
                duration = resultSet.getInt("Duration");
                timeUnit = TimeUnit.valueOf(resultSet.getString("DurationUnit"));
                sets = resultSet.getInt("Sets");
                reps = resultSet.getInt("Reps");
                weight = resultSet.getDouble("Weight");
                weightUnit = WeightUnit.valueOf(resultSet.getString("WeightUnit"));
                workoutExercise = createWorkoutExercise(exercise, xpValue, distance, distanceUnit, duration,
                        timeUnit, sets, reps, weight, weightUnit);

                exerciseComplete = resultSet.getBoolean("ExerciseCompleted");
                workoutSessionExerciseList.add(new WorkoutSessionExercise(workoutExercise, exerciseComplete));
            }

            if (workoutName != null) {
                workoutSession = new WorkoutSession(workoutName, scheduledDate, workoutComplete, workoutSessionExerciseList);
                workoutSessionList.add(workoutSession);
            }

            resultSet.close();
        }
        catch (Exception e) {
            sqlError(e);
        }

        return workoutSessionList;
    }

    /**
     * Inserts a new workout session into the database
     *
     * @param workoutSession the new workout session to insert into the database
     */
    @Override
    public void insertWorkoutSession(WorkoutSession workoutSession) {
        int workoutID, workoutSessionID, workoutExerciseID, workoutSessionExerciseID;

        try {
            resultSet = statement.executeQuery(
                    "SELECT	W.ID " +
                    "FROM	Workouts W " +
                    "WHERE	W.Name = " + workoutSession.getName());
            if (resultSet.next()) {
                workoutID = resultSet.getInt("ID");

                statement.executeQuery(
                        "INSERT INTO    WorkoutSessions (ScheduleDate, WorkoutID, Complete) " +
                        "VALUES         (" + format.print(workoutSession.getDate()) + ", " + workoutID + ", " + ", FALSE)");

                resultSet = statement.executeQuery(
                        "SELECT	MAX(WS.ID) " +
                        "FROM	WorkoutSessions WS");
                if (resultSet.next()) {
                    workoutSessionID = resultSet.getInt("ID");

                    resultSet = statement.executeQuery(
                            "SELECT		WE.ID " +
                            "FROM		WorkoutExercise WE " +
                            "LEFT JOIN	Exercise E " +
                            "			ON WE.ExerciseID = E.ID " +
                            "RIGHT JOIN	WorkoutContents WC " +
                            "			ON WE.ID = WC.ExerciseID " +
                            "WHERE		WC.WorkoutID = " + workoutID);

                    while (resultSet.next()) {
                        workoutExerciseID = resultSet.getInt("ID");
                        statement.executeQuery(
                                "INSERT INTO    WorkoutSessionExercises (WorkoutExerciseID, Complete) " +
                                "VALUES         (" + workoutExerciseID + ", FALSE) ");

                        resultSet2 = statement.executeQuery(
                                "SELECT	MAX(WSE.ID) " +
                                "FROM	WorkoutSessionExercises WSE ");
                        if (resultSet2.next()) {
                            workoutSessionExerciseID = resultSet2.getInt("ID");

                            statement.executeQuery(
                                    "INSERT INTO    WorkoutSessionContents (WorkoutSessionID, ExerciseID) " +
                                    "VALUES         (" + workoutSessionID + ", " + workoutSessionExerciseID + ") ");
                        }
                    }
                }
            }

            resultSet.close();
        }
        catch (Exception e) {
            sqlError(e);
        }
    }

    /**
     * Removes a workout session from the database, if it exists
     *
     * @param workoutSession the workout session to remove from the database
     */
    @Override
    public void removeWorkoutSession(WorkoutSession workoutSession) {
        int workoutSessionID;
        String command;

        try {
            resultSet = statement.executeQuery(
                    "SELECT	WS.ID " +
                    "FROM	WorkoutSessions WS " +
                    "WHERE	WS.ScheduledDate = " + format.print(workoutSession.getDate()));
            if (resultSet.next()) {
                workoutSessionID = resultSet.getInt("ID");

                resultSet = statement.executeQuery(
                        "SELECT	WSC.ExerciseID " +
                                "FROM	WorkoutSessionContents WSC " +
                                "WHERE	WSC.WorkoutSessionID = " + workoutSessionID);

                statement.executeQuery(
                        "DELETE FROM	WorkoutSessionContents WSC " +
                                "WHERE		    WSC.WorkoutSessionID = " + workoutSessionID);

                command = "DELETE FROM  WorkoutSessionExercises WSE" +
                        "WHERE        WSE.ID IN (";
                if (resultSet.next())
                    command += resultSet.getInt("ExerciseID");
                while (resultSet.next())
                    command += ", " + resultSet.getInt("ExerciseID");
                command += ")";
                statement.executeQuery(command);

                statement.executeQuery(
                        "DELETE FROM	WorkoutSession WS " +
                        "WHERE		    WS.ScheduledDate = " + format.print(workoutSession.getDate()));
            }

            resultSet.close();
        }
        catch (Exception e) {
            sqlError(e);
        }
    }

    /**
     * Toggles the completed state of a workout in the database
     *
     * @param workoutSession the workout to change the state of
     */
    @Override
    public void toggleWorkoutComplete(WorkoutSession workoutSession) {

    }

    /**
     * Gets a list of all workouts in the database
     *
     * @return a list of all workouts in the database
     */
    @Override
    public List<Workout> getWorkoutsList() {
        return null;
    }

    /**
     * Gets a list of names of all exercises in the database
     *
     * @return a list of names of all workouts in the database
     */
    @Override
    public List<String> getWorkoutNamesList() {
        return null;
    }

    /**
     * Retrieves a workout from the database with the name given as parameter
     *
     * @param workoutName the name of the workout to retrieve from the database
     * @return The workout with name workoutName, or null if no workout exists with that name
     */
    @Override
    public Workout getWorkout(String workoutName) {
        return null;
    }

    /**
     * Handles the creation of a workout exercise based on the values that are stored with a workout exercise
     * in the database
     *
     * @param exercise the exercise for the workout exercise
     * @param xpValue the xp value for the workout exercise
     * @param distance the recommended distance
     * @param distanceUnit the unit of measure for the distance
     * @param duration the recommended duration
     * @param timeUnit the unit of measure for the duration
     * @param sets the recommended number of sets
     * @param reps the recommended number of reps
     * @param weight the recommended weight
     * @param weightUnit the unit of measure for the weight
     * @return the workout exercise based on the passed values
     */
    private WorkoutExercise createWorkoutExercise(Exercise exercise, int xpValue, double distance,
                                                  DistanceUnit distanceUnit, int duration, TimeUnit timeUnit,
                                                  int sets, int reps, double weight, WeightUnit weightUnit) {
        WorkoutExercise workoutExercise = null;
        InterfaceExerciseQuantity exerciseQuantity;

        if (distance != NULL_NUM) {
            exerciseQuantity = new ExerciseDistance(distance, distanceUnit);
            workoutExercise = new WorkoutExerciseDistance(exercise, xpValue, (ExerciseDistance)exerciseQuantity);
        }
        else if (duration != NULL_NUM) {
            exerciseQuantity = new ExerciseDuration(duration, timeUnit);
            workoutExercise = new WorkoutExerciseDuration(exercise, xpValue, (ExerciseDuration)exerciseQuantity);

        }
        else if (sets != NULL_NUM && reps != NULL_NUM) {
            exerciseQuantity = new ExerciseSets(sets, reps);
            workoutExercise = new WorkoutExerciseSets(exercise, xpValue, (ExerciseSets)exerciseQuantity);

        }
        else if (weight != NULL_NUM) {
            exerciseQuantity = new ExerciseSetsAndWeight(sets, reps, weight, weightUnit);
            workoutExercise = new WorkoutExerciseSetsAndWeight(exercise, xpValue, (ExerciseSetsAndWeight)exerciseQuantity);
        }

        return workoutExercise;
    }

    /**
     * Gets the error message message of an SQL exception and prints the stack trace
     * @param e the exception thrown
     * @return the error message for the SQL exception
     */
    private String sqlError(Exception e)
    {
        String result; //the error message to print

        result = "SQL Error: " + e.getMessage();
        e.printStackTrace();;

        return result;
    }
}
