package com.payroll;

import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

class Employee {
    String empNumber;
    String name;
    String birthday;
    double hourlyRate;

    public Employee(String empNumber, String name, String birthday, double hourlyRate) {
        this.empNumber = empNumber;
        this.name = name;
        this.birthday = birthday;
        this.hourlyRate = hourlyRate;
    }
}

class AttendanceLog {
    String empNumber;
    LocalDate date;
    LocalTime login;
    LocalTime logout;

    public AttendanceLog(String empNumber, String date, String login, String logout) {
        this.empNumber = empNumber;
        this.date = LocalDate.parse(date);
        this.login = LocalTime.parse(login);
        this.logout = LocalTime.parse(logout);
    }
}

public class CsvPayrollSystem {
    static final LocalTime GRACE_PERIOD_END = LocalTime.of(8, 11);

    public static void main(String[] args) throws IOException {
        String employeeFilePath = "employees.csv";
        String attendanceFilePath = "attendance.csv";

        Map<String, Employee> employees = loadEmployees(employeeFilePath);
        List<AttendanceLog> attendanceLogs = loadAttendance(attendanceFilePath);

        Map<String, Map<Integer, List<AttendanceLog>>> logsByEmployeeWeek = groupLogsByEmployeeAndWeek(attendanceLogs);

        System.out.printf("%-10s %-20s %-12s %-6s %-10s %-10s\n",
            "Emp No", "Name", "Birthday", "Week", "Gross", "Net");

        for (String empId : logsByEmployeeWeek.keySet()) {
            Employee e = employees.get(empId);
            if (e == null) continue;

            Map<Integer, List<AttendanceLog>> weeks = logsByEmployeeWeek.get(empId);
            for (int weekNum : weeks.keySet()) {
                double totalHours = 0;
                for (AttendanceLog log : weeks.get(weekNum)) {
                    LocalTime login = log.login;
                    LocalTime logout = log.logout;
                    if (login.isAfter(GRACE_PERIOD_END)) {
                        long lateMinutes = Duration.between(GRACE_PERIOD_END, login).toMinutes();
                        totalHours -= lateMinutes / 60.0;
                    }
                    long minutesWorked = Duration.between(login, logout).toMinutes();
                    totalHours += minutesWorked / 60.0;
                }

                double gross = totalHours * e.hourlyRate;
                double net = calculateNetSalary(gross);

                System.out.printf("%-10s %-20s %-12s %-6d %-10.2f %-10.2f\n",
                    e.empNumber, e.name, e.birthday, weekNum, gross, net);
            }
        }
    }

    private static Map<String, Employee> loadEmployees(String fileName) throws IOException {
        Map<String, Employee> employees = new HashMap<>();
        BufferedReader br = new BufferedReader(new FileReader(fileName));
        br.readLine();
        String line;
        while ((line = br.readLine()) != null) {
            String[] parts = line.split(",");
            employees.put(parts[0], new Employee(parts[0], parts[1], parts[2], Double.parseDouble(parts[3])));
        }
        br.close();
        return employees;
    }

    private static List<AttendanceLog> loadAttendance(String fileName) throws IOException {
        List<AttendanceLog> logs = new ArrayList<>();
        BufferedReader br = new BufferedReader(new FileReader(fileName));
        br.readLine();
        String line;
        while ((line = br.readLine()) != null) {
            String[] parts = line.split(",");
            logs.add(new AttendanceLog(parts[0], parts[1], parts[2], parts[3]));
        }
        br.close();
        return logs;
    }

    private static Map<String, Map<Integer, List<AttendanceLog>>> groupLogsByEmployeeAndWeek(List<AttendanceLog> logs) {
        Map<String, Map<Integer, List<AttendanceLog>>> result = new HashMap<>();
        for (AttendanceLog log : logs) {
            String emp = log.empNumber;
            int week = log.date.get(WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear());
            result.computeIfAbsent(emp, k -> new HashMap<>()).computeIfAbsent(week, w -> new ArrayList<>()).add(log);
        }
        return result;
    }

    private static double calculateNetSalary(double gross) {
        double sss = calculateSSS(gross);
        double philhealth = Math.min(gross * 0.03, 1800);
        double pagibig = gross * 0.02;
        double withholding = calculateWithholdingTax(gross);
        return gross - (sss + philhealth + pagibig + withholding);
    }

    private static double calculateSSS(double gross) {
        if (gross <= 3250) return 135.00;
        if (gross <= 24750) return 1125.00;
        return 1350.00;
    }

    private static double calculateWithholdingTax(double gross) {
        if (gross <= 20833) return 0;
        if (gross <= 33332) return (gross - 20833) * 0.20;
        return (gross - 33332) * 0.25 + 2500;
    }
}
