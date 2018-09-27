package com.kickstarter.viewmodels

import UpdateUserEmailMutation
import android.support.annotation.NonNull
import com.kickstarter.libs.ActivityViewModel
import com.kickstarter.libs.Environment
import com.kickstarter.libs.rx.transformers.Transformers
import com.kickstarter.libs.rx.transformers.Transformers.values
import com.kickstarter.services.ApolloClientType
import com.kickstarter.ui.activities.ChangeEmailActivity
import rx.Observable
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject
import timber.log.Timber

interface ChangeEmailViewModel {

    interface Inputs {
        /** Call when update button has been clicked.  */
        fun updateEmailClicked(newEmail: String, currentPassword: String)

        /** Call when Resend Row is clicked. */
        fun resendEmailVerification()
    }

    interface Outputs {
        /** Emits the logged in user's email address.  */
        fun email(): Observable<String>

        /** Emits a boolean that determines if a user's email address is verified. */
        fun isEmailVerified(): Observable<Boolean>

        /** Emits a boolean that determines if a network call is in progress.  */
        fun showProgressBar(): Observable<Boolean>

        /** Emits the logged in user's email which we use get the success of the mutation. */
        fun success(): Observable<Void>
    }

    interface Errors {
        /** Emits a string to display when user could not be found.  */
        fun error(): Observable<String>
    }

    class ViewModel(@NonNull val environment: Environment) : ActivityViewModel<ChangeEmailActivity>(environment), Inputs, Outputs, Errors {

        val inputs: Inputs = this
        val outputs: Outputs = this
        val errors: Errors = this

        private val resendEmailVerification = PublishSubject.create<Void>()
        private val updateEmail = PublishSubject.create<Pair<String, String>>()

        private val email = BehaviorSubject.create<String>()
        private val isEmailVerified = BehaviorSubject.create<Boolean>()
        private val showProgressBar = BehaviorSubject.create<Boolean>()
        private val success = BehaviorSubject.create<Void>()

        private val error = BehaviorSubject.create<String>()

        private val apolloClient: ApolloClientType = environment.apolloClient()
        private val client = environment.webClient()

        init {

            this.apolloClient.userPrivacy()
                    .compose(bindToLifecycle())
                    .subscribe {
                        val email = it.me()?.email()
                        val verified = it.me()?.isEmailVerified
                        this@ViewModel.email.onNext(email)
                        this@ViewModel.isEmailVerified.onNext(verified)
                    }


            val updateEmailNotification = this.updateEmail
                    .switchMap { updateEmail(it).materialize() }
                    .compose(bindToLifecycle())
                    .share()

            updateEmailNotification
                    .compose(Transformers.errors())
                    .subscribe({ this.error.onNext(it.localizedMessage) })

            updateEmailNotification
                    .compose(values())
                    .subscribe({
                        emitData(it)
                    })
        }

        override fun resendEmailVerification() {
            this.resendEmailVerification.onNext(null)
        }

        override fun updateEmailClicked(newEmail: String, currentPassword: String) {
            this.updateEmail.onNext(Pair(newEmail, currentPassword))
        }

        override fun email(): Observable<String> = this.email

        override fun isEmailVerified(): Observable<Boolean> = this.isEmailVerified

        override fun showProgressBar(): Observable<Boolean> = this.showProgressBar

        override fun success(): Observable<Void> = this.success

        override fun error(): Observable<String> = this.error

        private fun emitData(it: UpdateUserEmailMutation.Data) {
            this.email.onNext(it.updateUserAccount()?.user()?.email())
            this.success.onNext(null)
        }

         fun resendEmail() {
            this.client.resendEmailVerification()
                    .compose(bindToLifecycle())
                    .doOnError { Timber.e("THIS ISNT WORKING $it.message") }
                    .subscribe { Timber.d("success")  }
        }

        private fun updateEmail(emailAndPassword: Pair<String, String>): Observable<UpdateUserEmailMutation.Data> {
            return this.apolloClient.updateUserEmail(emailAndPassword.first, emailAndPassword.second)
                    .doOnSubscribe { this.showProgressBar.onNext(true) }
                    .doAfterTerminate { this.showProgressBar.onNext(false) }
        }

    }
}