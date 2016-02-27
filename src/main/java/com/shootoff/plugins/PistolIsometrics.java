package com.shootoff.plugins;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.shootoff.camera.Shot;
import com.shootoff.gui.Hit;
import com.shootoff.util.NamedThreadFactory;

import javafx.scene.Group;

public class PistolIsometrics extends TrainingExerciseBase implements TrainingExercise {
	private static final int START_DELAY = 5; // s
	private static final int NOTICE_INTERVAL = 30; // s
	private static final int CORE_POOL_SIZE = 4;
	private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(CORE_POOL_SIZE,
			new NamedThreadFactory("PistolIsometricsExercise"));
	private boolean repeatExercise = true;
	private TrainingExerciseBase api;

	public PistolIsometrics() {}

	public PistolIsometrics(List<Group> targets) {
		super(targets);
		api = super.getInstance();
	}

	@Override
	public ExerciseMetadata getInfo() {
		return new ExerciseMetadata("Pistol Isometrics", "1.0", "phrack",
				"This exercise walks you through hold exercises to strengthen "
						+ "arm and hand muscles that help you shoot a pistol accurately. "
						+ "You will be asked to shoot, then you must shoot until you hit "
						+ "any target. Once you hit a target, hold your exact position for "
						+ "60 seconds. The exercise will tell you how much longer there is "
						+ "every 30 seconds. After you've held for 60 seconds, you will "
						+ "get two minutes to rest before you must fire again.");
	}

	@Override
	public void init() {
		startRound();
	}

	private void startRound() {
		super.pauseShotDetection(true);
		// Standard sound file shipped with ShootOFF
		playSound(new File("sounds/voice/shootoff-makeready.wav"));

		executorService.schedule(new Fire(), START_DELAY, TimeUnit.SECONDS);
	}

	/*
	 * Load a sound file from the exercise's JAR file into a
	 * BufferedInputStream. This specific type of stream is required to play
	 * audio.
	 */
	private InputStream getSoundStream(String soundResource) {
		return new BufferedInputStream(PistolIsometrics.class.getResourceAsStream(soundResource));
	}

	private class Fire implements Runnable {
		@Override
		public void run() {
			if (!repeatExercise) return;

			api.pauseShotDetection(false);

			InputStream fire = getSoundStream("/sounds/fire.wav");
			TrainingExerciseBase.playSound(fire);
		}
	}

	private class TimeNotice implements Runnable {
		private int timeLeft;
		private boolean justShot;

		public TimeNotice(int timeLeft, boolean justShot) {
			this.timeLeft = timeLeft;
			this.justShot = justShot;
		}

		@Override
		public void run() {
			if (!repeatExercise) return;

			InputStream notice;

			switch (timeLeft) {
			case 30:
				notice = getSoundStream("/sounds/30remaining.wav");
				break;

			case 60:
				notice = getSoundStream("/sounds/60remaining.wav");
				break;

			case 90:
				notice = getSoundStream("/sounds/90remaining.wav");
				break;

			default: // happens when timeLeft = 0 in this
				if (justShot) {
					notice = getSoundStream("/sounds/relax120.wav");
					timeLeft = 120;
					justShot = false;
				} else {
					notice = getSoundStream("/sounds/congrats-complete.wav");
					executorService.schedule(() -> startRound(), START_DELAY, TimeUnit.SECONDS);
				}
			}

			TrainingExerciseBase.playSound(notice);
			executorService.schedule(new TimeNotice(timeLeft - NOTICE_INTERVAL, justShot), NOTICE_INTERVAL,
					TimeUnit.SECONDS);
		}
	}

	@Override
	public void reset(List<Group> targets) {
		repeatExercise = false;
		executorService.shutdownNow();

		repeatExercise = true;
		executorService = Executors.newScheduledThreadPool(CORE_POOL_SIZE,
				new NamedThreadFactory("PistolIsometricsExercise"));
		startRound();
	}

	@Override
	public void shotListener(Shot shot, Optional<Hit> hit) {
		if (!hit.isPresent()) {
			new Fire().run();
		} else {
			super.pauseShotDetection(true);
			
			InputStream hold = getSoundStream("/sounds/hold60.wav");
			TrainingExerciseBase.playSound(hold);

			executorService.schedule(new TimeNotice(30, true), NOTICE_INTERVAL, TimeUnit.SECONDS);
		}
	}

	@Override
	public void destroy() {
		repeatExercise = false;
		executorService.shutdownNow();
		super.destroy();
	}
}
