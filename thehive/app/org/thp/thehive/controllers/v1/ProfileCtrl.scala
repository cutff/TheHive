package org.thp.thehive.controllers.v1

import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.AuthorizationError
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.scalligraph.steps.PagedResult
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.InputProfile
import org.thp.thehive.models.{Permissions, Profile}
import org.thp.thehive.services.{ProfileSrv, ProfileSteps}
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Failure

@Singleton
class ProfileCtrl @Inject() (entrypoint: Entrypoint, @Named("with-thehive-schema") db: Database, properties: Properties, profileSrv: ProfileSrv)
    extends QueryableCtrl {

  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, ProfileSteps](
    "getProfile",
    FieldsParser[IdOrName],
    (param, graph, _) => profileSrv.get(param.idOrName)(graph)
  )
  val entityName: String                           = "profile"
  val publicProperties: List[PublicProperty[_, _]] = properties.profile ::: metaProperties[ProfileSteps]

  val initialQuery: Query =
    Query.init[ProfileSteps]("listProfile", (graph, _) => profileSrv.initSteps(graph))

  val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, ProfileSteps, PagedResult[Profile with Entity]](
    "page",
    FieldsParser[OutputParam],
    (range, profileSteps, _) => profileSteps.page(range.from, range.to, range.extraData.contains("total"))
  )
  override val outputQuery: Query = Query.output[Profile with Entity]

  def create: Action[AnyContent] =
    entrypoint("create profile")
      .extract("profile", FieldsParser[InputProfile])
      .authTransaction(db) { implicit request => implicit graph =>
        val profile: InputProfile = request.body("profile")
        if (request.isPermitted(Permissions.manageProfile)) {
          profileSrv.create(profile.toProfile).map(createdProfile => Results.Created(createdProfile.toJson))
        } else
          Failure(AuthorizationError("You don't have permission to create profiles"))
      }

  def get(profileId: String): Action[AnyContent] =
    entrypoint("get profile")
      .authRoTransaction(db) { _ => implicit graph =>
        profileSrv
          .getOrFail(profileId)
          .map { profile =>
            Results.Ok(profile.toJson)
          }
      }

  def update(profileId: String): Action[AnyContent] =
    entrypoint("update profile")
      .extract("profile", FieldsParser.update("profile", properties.profile))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("profile")
        if (request.isPermitted(Permissions.manageProfile)) {
          profileSrv
            .update(_.get(profileId), propertyUpdaters)
            .flatMap { case (profileSteps, _) => profileSteps.getOrFail("Profile") }
            .map(profile => Results.Ok(profile.toJson))
        } else
          Failure(AuthorizationError("You don't have permission to update profiles"))
      }

  def delete(profileId: String): Action[AnyContent] =
    entrypoint("delete profile")
      .authPermittedTransaction(db, Permissions.manageProfile) { implicit request => implicit graph =>
        profileSrv
          .getOrFail(profileId)
          .flatMap(profileSrv.remove)
          .map(_ => Results.NoContent)
      }
}