/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.utils.via;

import org.matsim.core.utils.misc.Time;

import java.io.*;

/**
 * Creates various CSV files in order to display the SBB clock in Via.
 *
 * @author mrieser / SBB
 */
public class CreateClockXY {

    private static final double deg2rad = Math.PI / 180;

    private boolean showSeconds = true;
    private boolean fixSecondsAt12 = false;
    private boolean jump2go = false; // include 2-second stop for second digit at 12
    private double startTime = 0.0;
    private double endTime = 30d * 3600;
    private final double[] x = new double[60];
    private final double[] y = new double[60];

    public CreateClockXY() {
        for (int i = 0; i < 60; i++) {
            double deg = i * 6d;
            double rad = deg * deg2rad;
            this.x[i] = Math.sin(rad);
            this.y[i] = Math.cos(rad);
        }
    }

	public static void main(String[] args) {
		CreateClockXY clock = new CreateClockXY();
		clock.createBackground("clockBackground.csv");
		clock.fixSecondsAt12(true);
		clock.create("clockFixedSeconds.csv");
		clock.fixSecondsAt12(false);
		clock.create("clockSeconds.csv");
		clock.setJump2go(true);
		clock.create("clockJump2Go.csv");
		clock.showSeconds(false);
		clock.create("clockNoSeconds.csv");
	}

	public void showSeconds(boolean show) {
		this.showSeconds = show;
	}

	public void fixSecondsAt12(boolean fix) {
		this.fixSecondsAt12 = fix;
	}

	public void setJump2go(boolean jump2go) {
		this.jump2go = jump2go;
	}

	public void setStartTime(double time) {
		this.startTime = time;
	}

	public void setEndTime(double time) {
		this.endTime = time;
	}

	public void create(final String filename) {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
			writeHeader(writer);
			createIndices(writer);
			createHoursDigit(writer);
			createMinutesDigit(writer);
			if (this.showSeconds) {
				createSecondsDigit(writer);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public void createBackground(final String filename) {
		// Zifferblatt: radius=50
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
			writeHeader(writer);
			write(writer, 0.0, "BACK", 0, 0, 0, 0, "WHITE", 0, 50, "POINT");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

	}

	private void createIndices(Writer writer) throws IOException {
		// Zifferblatt: radius=50
		// Minutenindex: 1.4  x  3.5
		// Stundenindex: 3.5  x  12
		// Index-Versatz: 1.5 --> 48.5

		for (int i = 0; i < 60; i++) {
			double x = this.x[i];
			double y = this.y[i];
			if (i % 5 == 0) {
				// Stundenindex
				write(writer, 0.0, "INDEX_" + i, 48.5 * x, 48.5 * y, 36.5 * x, 36.5 * y, "BLACK", 3.5, 0, "LINE");
			} else {
				// Minutenindex
				write(writer, 0.0, "INDEX_" + i, 48.5 * x, 48.5 * y, 45 * x, 45 * y, "BLACK", 1.4, 0, "LINE");
			}
		}
	}

	private void createHoursDigit(Writer writer) throws IOException {
		// Laenge: 12 (Achse) 32
		// Breite: 6.4 (Achse) 5.2 --> 5.7
		int startMin = (int) (this.startTime / 60);
		int endMin = (int) Math.ceil(this.endTime / 60);
		for (int minute = startMin; minute < endMin; minute++) {
			double hour = minute / 60.0;
			double degree = hour * 5 * 6; // 5 minutes per hour, 6 degrees per minute
			double rad = degree * deg2rad;
			double x = Math.sin(rad);
			double y = Math.cos(rad);
			write(writer, minute * 60d, "HOUR", -12d * x, -12d * y, 32d * x, 32d * y, "BLACK", 5.7, 0, "LINE");
		}
	}

	private void createMinutesDigit(Writer writer) throws IOException {
		// Laenge: 12 (Achse) 46
		// Breite: 5.2 (Achse) 3.6  --> 4.2
		int startMin = (int) (this.startTime / 60);
		int endMin = (int) Math.ceil(this.endTime / 60);
		for (int minute = startMin; minute < endMin; minute++) {
			int position = minute % 60;
			double x = this.x[position];
			double y = this.y[position];
			write(writer, minute * 60d, "MINUTE", -12d * x, -12d * y, 46d * x, 46d * y, "BLACK", 4.2, 0, "LINE");
		}
	}

	private void createSecondsDigit(Writer writer) throws IOException {
		// Laenge: 16.5 (Achse) 31.2
		// Breite: 1.4
		// Radius Kellenkopf: 5.25
		if (this.fixSecondsAt12) {
			double x = this.x[0];
			double y = this.y[0];
			write(writer, 0.0, "SECOND", 31.2 * x, 31.2 * y, -16.5 * x, -16.5 * y, "RED", 1.4, 5.25, "LINE");
			return;
		}

		int startSec = (int) this.startTime;
		for (int sec = startSec; sec < this.endTime; sec++) {
			int position = sec % 60;
			double x = this.x[position];
			double y = this.y[position];
			double shownSec = sec;
			if (this.jump2go) {
				double minutes = sec / 60d;
				double seconds = sec - (minutes * 60);
				seconds = seconds * 60.0 / 58.0;
				if (seconds > 60) {
					seconds = 60;
				}
				shownSec = minutes * 60d + seconds;
			}
			write(writer, shownSec, "SECOND", 31.2 * x, 31.2 * y, -16.5 * x, -16.5 * y, "RED", 1.4, 5.25, "LINE");
		}
	}

	private void writeHeader(Writer writer) throws IOException {
		writer.write("TIME,ID,X1,Y1,X2,Y2,COLOR,LINEWIDTH,POINTSIZE,STYLE\n");
	}

	private void write(Writer writer, double time, String id, double x1, double y1, double x2, double y2, String color, double lineWidth, double pointSize, String style) throws IOException {
		writer.append(Time.writeTime(time));
		writer.append(',');
		writer.append(id);
		writer.append(',');
		writer.append(Double.toString(x1));
		writer.append(',');
		writer.append(Double.toString(y1));
		writer.append(',');
		writer.append(Double.toString(x2));
		writer.append(',');
		writer.append(Double.toString(y2));
		writer.append(',');
		writer.append(color);
		writer.append(',');
		writer.append(Double.toString(lineWidth));
		writer.append(',');
		writer.append(Double.toString(pointSize));
		writer.append(',');
		writer.append(style);
		writer.append('\n');
	}
}
