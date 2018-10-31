package com.prisma.deploy.connector.jdbc.database

import com.prisma.connector.shared.jdbc.SlickDatabase
import com.prisma.deploy.connector.jdbc.JdbcBase
import com.prisma.shared.models.{Model, Project, TypeIdentifier}
import org.jooq.impl.DSL._
import com.prisma.shared.models.TypeIdentifier.{ScalarTypeIdentifier, TypeIdentifier}
import org.jooq.{DataType, Field, SQLDialect, impl}
import org.jooq.impl.{DSL, SQLDataType}
import org.jooq.util.mysql.MySQLDataType
import org.jooq.util.postgres.PostgresDataType
import slick.dbio.DBIO

import scala.concurrent.ExecutionContext

case class JdbcDeployDatabaseMutationBuilder(slickDatabase: SlickDatabase)(implicit ec: ExecutionContext) extends JdbcBase {
  def createClientDatabaseForProject(projectId: String) = {
    val schema = changeDatabaseQueryToDBIO(sql.createSchema(projectId))()
    val table = changeDatabaseQueryToDBIO(
      sql
        .createTable(name(projectId, "_RelayId"))
        .column("id", SQLDataType.VARCHAR(36).nullable(false))
        .column("stableModelIdentifier", SQLDataType.VARCHAR(25).nullable(false))
        .constraint(constraint("pk_RelayId").primaryKey(name(projectId, "_RelayId", "id"))))()

    DBIO.seq(schema, table)
  }

  def truncateProjectTables(project: Project) = {
    val listTableNames: List[String] =
      project.models.flatMap(model => model.fields.collect { case field if field.isScalar && field.isList => s"${model.dbName}_${field.dbName}" })

    val tables = Vector("_RelayId") ++ project.models.map(_.dbName) ++ project.relations.map(_.relationTableName) ++ listTableNames
    val queries = tables.map(tableName => {
      changeDatabaseQueryToDBIO(sql.truncate(name(project.id, tableName)).cascade())()
    })

    DBIO.seq(queries: _*)
  }

  def deleteProjectDatabase(projectId: String) = changeDatabaseQueryToDBIO(sql.dropSchemaIfExists(projectId).cascade())()

  //  def dropTable(projectId: String, tableName: String)                              = sqlu"""DROP TABLE "#$projectId"."#$tableName""""
//  def dropScalarListTable(projectId: String, modelName: String, fieldName: String) = sqlu"""DROP TABLE "#$projectId"."#${modelName}_#${fieldName}""""

  def createModelTable(projectId: String, model: Model) = {
    val idField = model.idField_!
    val idColField: Field[_] = if (idField.isAutoGenerated) {
      sql.dialect() match {
        case SQLDialect.POSTGRES => field(s"${name(idField.dbName).toString} SERIAL NOT NULL")
        case SQLDialect.MYSQL    => field(s"${name(idField.dbName).toString} INT NOT NULL AUTO_INCREMENT")
        case _                   => ???
      }
    } else {
      field(name(idField.dbName), sqlTypeForScalarTypeIdentifier(idField.isList, idField.typeIdentifier).nullable(false))
    }

    val query = sql
      .createTable(name(projectId, model.dbName))
      .column(idColField)
      .constraint(constraint(s"pk_${name(model.dbName).unquotedName().toString}").primaryKey(name(projectId, model.dbName, idField.dbName)))

    changeDatabaseQueryToDBIO(query)()

//    sqlu"""CREATE TABLE "#$projectId"."#${model.dbName}"
//    ("#${idField.dbName}" #$sqlType NOT NULL,
//    PRIMARY KEY ("#${idField.dbName}")
//    )"""
  }

  def sqlTypeForScalarTypeIdentifier(isList: Boolean, t: TypeIdentifier): DataType[_] = {
    if (isList) {
      return SQLDataType.CLOB
    }

    // todos
    // - Text / mediumtext mapping to clob might be an issue down the road
    //
    // Potential solutions:
    // - Factor out an interface for every SQL connector that specifies how to map types or even entire fields

    // From Postgres
//    case TypeIdentifier.String   => "text"
//    case TypeIdentifier.Boolean  => "boolean"
//    case TypeIdentifier.Int      => "int"
//    case TypeIdentifier.Float    => "Decimal(65,30)"
//    case TypeIdentifier.Cuid     => "varchar (25)"
//    case TypeIdentifier.Enum     => "text"
//    case TypeIdentifier.Json     => "text"
//    case TypeIdentifier.DateTime => "timestamp (3)"
//    case TypeIdentifier.UUID     => "uuid"

    // From MySQL
//    case TypeIdentifier.String   => "mediumtext"
//    case TypeIdentifier.Boolean  => "boolean"
//    case TypeIdentifier.Int      => "int"
//    case TypeIdentifier.Float    => "Decimal(65,30)"
//    case TypeIdentifier.Cuid     => "char(25)"
//    case TypeIdentifier.UUID     => "char(36)"
//    case TypeIdentifier.Enum     => "varchar(191)"
//    case TypeIdentifier.Json     => "mediumtext"
//    case TypeIdentifier.DateTime => "datetime(3)"

    t match {
      case TypeIdentifier.String   => SQLDataType.CLOB
      case TypeIdentifier.Boolean  => SQLDataType.BOOLEAN
      case TypeIdentifier.Int      => SQLDataType.INTEGER
      case TypeIdentifier.Float    => SQLDataType.DECIMAL(65, 30)
      case TypeIdentifier.Cuid     => SQLDataType.CHAR(25)
      case TypeIdentifier.UUID     => SQLDataType.UUID //"char(36)" // TODO: verify whether this is the right thing to do
      case TypeIdentifier.Enum     => SQLDataType.VARCHAR(191) //"varchar(191)"
      case TypeIdentifier.Json     => SQLDataType.CLOB
      case TypeIdentifier.DateTime => SQLDataType.TIMESTAMP(3) // "datetime(3)"
      case _                       => ???
    }
  }

//  def createScalarListTable(projectId: String, model: Model, fieldName: String, typeIdentifier: ScalarTypeIdentifier) = {
//    val sqlType = sqlTypeForScalarTypeIdentifier(typeIdentifier)
//    sqlu"""CREATE TABLE "#$projectId"."#${model.dbName}_#$fieldName"
//    ("nodeId" VARCHAR (25) NOT NULL REFERENCES "#$projectId"."#${model.dbName}" ("#${model.dbNameOfIdField_!}"),
//    "position" INT NOT NULL,
//    "value" #$sqlType NOT NULL,
//    PRIMARY KEY ("nodeId", "position")
//    )"""
//  }
//
//  def updateScalarListType(projectId: String, modelName: String, fieldName: String, typeIdentifier: ScalarTypeIdentifier) = {
//    val sqlType = sqlTypeForScalarTypeIdentifier(typeIdentifier)
//    sqlu"""ALTER TABLE "#$projectId"."#${modelName}_#${fieldName}" DROP INDEX "value", CHANGE COLUMN "value" "value" #$sqlType, ADD INDEX "value" ("value" ASC)"""
//  }
//
//  def renameScalarListTable(projectId: String, modelName: String, fieldName: String, newModelName: String, newFieldName: String) = {
//    sqlu"""ALTER TABLE "#$projectId"."#${modelName}_#${fieldName}" RENAME TO "#${newModelName}_#${newFieldName}""""
//  }
//
//  def renameTable(projectId: String, name: String, newName: String) = sqlu"""ALTER TABLE "#$projectId"."#$name" RENAME TO "#$newName";"""
//
//  def createColumn(
//      projectId: String,
//      tableName: String,
//      columnName: String,
//      isRequired: Boolean,
//      isUnique: Boolean,
//      isList: Boolean,
//      typeIdentifier: TypeIdentifier.ScalarTypeIdentifier
//  ) = {
//
//    val sqlType    = sqlTypeForScalarTypeIdentifier(typeIdentifier)
//    val nullString = if (isRequired) "NOT NULL" else "NULL"
//    val uniqueAction = isUnique match {
//      case true  => sqlu"""CREATE UNIQUE INDEX "#$projectId.#$tableName.#$columnName._UNIQUE" ON "#$projectId"."#$tableName"("#$columnName" ASC);"""
//      case false => DBIOAction.successful(())
//    }
//
//    val addColumn = sqlu"""ALTER TABLE "#$projectId"."#$tableName" ADD COLUMN "#$columnName" #$sqlType #$nullString"""
//
//    DBIOAction.seq(addColumn, uniqueAction)
//  }
//
//  def deleteColumn(projectId: String, tableName: String, columnName: String) = {
//    sqlu"""ALTER TABLE "#$projectId"."#$tableName" DROP COLUMN "#$columnName""""
//  }
//
//  def updateColumn(
//      projectId: String,
//      tableName: String,
//      oldColumnName: String,
//      newColumnName: String,
//      newIsRequired: Boolean,
//      newIsList: Boolean,
//      newTypeIdentifier: ScalarTypeIdentifier
//  ) = {
//    val nulls   = if (newIsRequired) { "SET NOT NULL" } else { "DROP NOT NULL" }
//    val sqlType = sqlTypeForScalarTypeIdentifier(newTypeIdentifier)
//    val renameIfNecessary =
//      if (oldColumnName != newColumnName) sqlu"""ALTER TABLE "#$projectId"."#$tableName" RENAME COLUMN "#$oldColumnName" TO "#$newColumnName""""
//      else DBIOAction.successful(())
//
//    DBIOAction.seq(
//      sqlu"""ALTER TABLE "#$projectId"."#$tableName" ALTER COLUMN "#$oldColumnName" TYPE #$sqlType""",
//      sqlu"""ALTER TABLE "#$projectId"."#$tableName" ALTER COLUMN "#$oldColumnName" #$nulls""",
//      renameIfNecessary
//    )
//  }
//
//  def addUniqueConstraint(projectId: String, tableName: String, columnName: String, typeIdentifier: TypeIdentifier, isList: Boolean) = {
//    sqlu"""CREATE UNIQUE INDEX "#$projectId.#$tableName.#$columnName._UNIQUE" ON "#$projectId"."#$tableName"("#$columnName" ASC);"""
//  }
//
//  def removeUniqueConstraint(projectId: String, tableName: String, columnName: String) = {
//    sqlu"""DROP INDEX "#$projectId"."#$projectId.#$tableName.#$columnName._UNIQUE""""
//  }
//
//  def createRelationTable(projectId: String, relationTableName: String, modelA: Model, modelB: Model) = {
//
//    val sqlTypeForIdOfModelA = sqlTypeForScalarTypeIdentifier(modelA.idField_!.typeIdentifier)
//    val sqlTypeForIdOfModelB = sqlTypeForScalarTypeIdentifier(modelB.idField_!.typeIdentifier)
//    val tableCreate          = sqlu"""CREATE TABLE "#$projectId"."#$relationTableName" (
//    "id" CHAR(25)  NOT NULL,
//    PRIMARY KEY ("id"),
//    "A" #$sqlTypeForIdOfModelA  NOT NULL,
//    "B" #$sqlTypeForIdOfModelB  NOT NULL,
//    FOREIGN KEY ("A") REFERENCES "#$projectId"."#${modelA.dbName}"("#${modelA.dbNameOfIdField_!}") ON DELETE CASCADE,
//    FOREIGN KEY ("B") REFERENCES "#$projectId"."#${modelB.dbName}"("#${modelA.dbNameOfIdField_!}") ON DELETE CASCADE)
//    ;"""
//
//    val indexCreate = sqlu"""CREATE UNIQUE INDEX "#${relationTableName}_AB_unique" on  "#$projectId"."#$relationTableName" ("A" ASC, "B" ASC)"""
//    val indexA      = sqlu"""CREATE INDEX "#${relationTableName}_A" on  "#$projectId"."#$relationTableName" ("A" ASC)"""
//    val indexB      = sqlu"""CREATE INDEX "#${relationTableName}_B" on  "#$projectId"."#$relationTableName" ("B" ASC)"""
//
//    DBIOAction.seq(tableCreate, indexCreate, indexA, indexB)
//  }
//
//  def createRelationColumn(projectId: String, model: Model, references: Model, column: String) = {
//    val sqlType    = sqlTypeForScalarTypeIdentifier(model.idField_!.typeIdentifier)
//    val isRequired = false //field.exists(_.isRequired)
//    val nullString = if (isRequired) "NOT NULL" else "NULL"
//    val addColumn  = sqlu"""ALTER TABLE "#$projectId"."#${model.dbName}" ADD COLUMN "#$column" #$sqlType #$nullString
//                            REFERENCES "#$projectId"."#${references.dbName}"(#${references.dbNameOfIdField_!}) ON DELETE SET NULL;"""
//    addColumn
//  }
//
//  private def sqlTypeForScalarTypeIdentifier(typeIdentifier: ScalarTypeIdentifier): String = {
//    typeIdentifier match {
//      case TypeIdentifier.String   => "text"
//      case TypeIdentifier.Boolean  => "boolean"
//      case TypeIdentifier.Int      => "int"
//      case TypeIdentifier.Float    => "Decimal(65,30)"
//      case TypeIdentifier.Cuid     => "varchar (25)"
//      case TypeIdentifier.Enum     => "text"
//      case TypeIdentifier.Json     => "text"
//      case TypeIdentifier.DateTime => "timestamp (3)"
//      case TypeIdentifier.UUID     => "uuid"
//    }
//  }
}