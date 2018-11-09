package ru.yandex.subbota_job.notes.viewModel

class Result<out R>(private val _data:R, val throwable:Throwable?){
	val data: R get() = if (throwable==null) data else throw throwable
}
