package jobs

import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport

class JobModule extends AbstractModule with AkkaGuiceSupport {
  def configure(): Unit = {
    bindActor[SchedulerActor]("scheduler-actor")
    bind(classOf[Scheduler]).asEagerSingleton()
  }
}
