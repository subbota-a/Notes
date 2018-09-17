package ru.yandex.subbota_job.notes.executor

import java.util.concurrent.Executors

class Executors{
	companion object {
		val sequence = Executors.newSingleThreadExecutor()
	}
}