package ru.spbstu.terrai.implementation

import ru.spbstu.terrai.core.*
import kotlin.math.sqrt


enum class State { JUST_EXPLORATION, ON_TRACE_TO_UNKNOWN_LOCATION, ON_TRACE_TO_FINISH }


class FirstPlayer : AbstractPlayer() {
    //lateinit var finishLocation: Location
    var finishFound = false

    var numberOfWormholes = 0

    /** <выходной Wormhole> to <координаты входа> **/
    var outputWormholeToInputLocation = mutableMapOf<Wormhole, Location>()

    /** <выходной Wormhole> to <координаты выхода> **/
    var outputWormholeToOutputLocation = mutableMapOf<Wormhole, Location>()

    /** <выходной Wormhole> to <условный номер> **/
    var outputWormholeToId = mutableMapOf<Wormhole, Int>()

    //val environments = mutableListOf<>()
    val currentWormhole = 0
    private val traceQueue = mutableListOf<Move>()
    private var currentState = State.JUST_EXPLORATION
    private val roomMap = mutableMapOf<Location, Room>()

    //может лучше сделать это значение адаптивным?
    //var criticalDistance = 10 //максимальное среднее удаление от ближайшей к старту неиследованной ячейки
    //private val state: StateOfMoving = StateOfMoving.WALL_FINDING_AND_EXPLORATION
    //var walls = mutableSetOf<Location>()
    private lateinit var currLocation: Location

    fun getFinishLocation(): Location {
        assert(finishFound)
        return roomMap.filter { it.value is Exit }.keys.random()
    }
    //private val roomMap = mutableMapOf<Location, Room>()


    //private val unknownAvailableRoomsLocations = mutableListOf<Location>()

    // unknownRoomLocation to sourceLocation (откуда можно попасть в эту неизвестную комнату)
    private val unknownAvailableSet = mutableSetOf<Location>()


    override fun setStartLocationAndSize(location: Location, width: Int, height: Int) {
        super.setStartLocationAndSize(location, width, height)
        currLocation = location
        println(currLocation)

        //environments += Environment(0)
        //environments[0].
        roomMap[currLocation] = Entrance
        updateUnknownAvailableRooms(currLocation)
    }

    private var lastMove: Move = WaitMove

    //стек ходов
    private val decisions = mutableListOf<Direction>()


    private var treasureFound = false


    override fun getNextMove(): Move {
        println("STATE:" + currentState)

        when (currentState) {
            State.JUST_EXPLORATION -> {
                //определяем направление к неизвестной ячейке
                val nextUnknown =
                    Direction.values().filter { it + currLocation !in roomMap }
                if (treasureFound && finishFound && findTraces(currLocation)[getFinishLocation()] != null) {//костыль
                    currentState = State.ON_TRACE_TO_FINISH
                    traceQueue.addAll(findTraces(currLocation)[getFinishLocation()]!!)
                    println("current location:" + currLocation)
                    println("finish location:" + getFinishLocation())
                    println("finish location:" + roomMap.filter { it.value is Exit }.keys.random())
                    println(findDirs(currLocation)[getFinishLocation()]!!.toString())
                    return getNextMove()
                } else if (nextUnknown.isNotEmpty()) {
                    val direction =
                        nextUnknown.minByOrNull {//такой выбор направления обеспечивает большую сжатость исследованной области
                            (it + currLocation).getAverageDistance(
                                unknownAvailableSet.filter
                                { location ->
                                    (location.x in currLocation.x - 500..currLocation.x + 500) &&
                                            (location.y in currLocation.y - 500..currLocation.y + 500)
                                }
                            )
                        }!!
                    decisions += direction
                    println(direction)
                    //println(direction + currentLocation)
                    this.lastMove = WalkMove(direction)
                    return this.lastMove
                } else {
                    return if (unknownAvailableSet.isNotEmpty()) {
                        currentState = State.ON_TRACE_TO_UNKNOWN_LOCATION
                        println("unknownAvailableSet:" + unknownAvailableSet)
                        println("currntLocation:" + currLocation)
                        traceQueue.addAll(findBestTraceToUnknownRoom()!!)
                        getNextMove()
                    } else {
                        error("there is no treasure anywhere!")
                    }
                }

            }
            State.ON_TRACE_TO_UNKNOWN_LOCATION -> {
                return if (traceQueue.isNotEmpty()) {
                    this.lastMove = traceQueue.removeFirst()
                    this.lastMove
                } else {
                    currentState = State.JUST_EXPLORATION;
                    getNextMove();
                }
            }

            State.ON_TRACE_TO_FINISH -> {
                println(currLocation.toString() + " finish:" + getFinishLocation().toString())
                this.lastMove = traceQueue.removeFirst()
                return this.lastMove
            };
        }

    }

    private fun updateUnknownAvailableRooms(location: Location) {
        val unknownNeighbors = Direction.values().map { it + location }.filter { !roomMap.containsKey(it) }
        val visitedNeighbors = Direction.values().map { it + location }.filter { roomMap.containsKey(it) }
        unknownAvailableSet.addAll(unknownNeighbors)
        unknownAvailableSet.removeAll(visitedNeighbors.toSet())
    }

    override fun setMoveResult(result: MoveResult) {
        val newLocation = (lastMove as? WalkMove)?.let { it.direction + currLocation } ?: currLocation
        val room = result.room
        //environments[currentEnvironment].

        roomMap[newLocation] = room
        println(newLocation.toString() + ":" + roomMap[newLocation])
        //println("result: [room:" + result.room + "] [status:" + result.status + "]" + "[successful:" + result.successful + "]"+ "[cond:" + result.condition + "]")
        if (result.successful) {
            when (room) {
                is Wormhole -> {
                    println("wormhole!!")
                    if (outputWormholeToOutputLocation.containsKey(room)) {
                        updateMapByWormHole(newLocation, room)
                        currLocation = outputWormholeToOutputLocation[room]!!
                    } else {
                        numberOfWormholes++
                        currLocation = Location(numberOfWormholes * 1000, numberOfWormholes * 1000)
                        outputWormholeToId[room] = numberOfWormholes
                        outputWormholeToOutputLocation[room] = currLocation
                        outputWormholeToInputLocation[room] = newLocation
                    }
                    //оставляем здесь выходной wormhole
                    //roomMap[newLocation] = room
                }
                is WithContent -> {
                    if (!treasureFound && result.condition.hasTreasure) {
                        println("treasure room")
                        treasureFound = true
                        currLocation = newLocation
                        ///roomMap[currentLocation] = room
                    }
                }
                is Exit -> {
                    println("EXIT!!")
                    finishFound = true
                    currLocation = newLocation
                    //roomMap[currentLocation] = room
                }
                else -> {
                    assert(room is Empty)
                    println("empty room")
                    currLocation = newLocation
                }
            }
        } else { //стена
            println("wall")
            decisions.removeAt(decisions.size - 1)
        }
        updateUnknownAvailableRooms(currLocation)
        //if(Location(1000, 1000) in roomMap) error("WTF?")
    }

    //возвращает маршрут к соседу неисследованной ячейки
    fun findBestTraceToUnknownRoom(): List<Move>? {
        val traces = findTraces(currLocation)
        val tracesSet = mutableSetOf<List<Move>>()
        //println(traces)
        unknownAvailableSet.forEach { unknownLocation ->
            val bestNeighbourToDirection = Direction.values().map { it + unknownLocation to it.turnBack() }
                .filter {/*
                    println("трасса к соседу:" + traces[it.first].toString() + " " + (it.first in traces.keys).toString() + " location:" + it.first)
                    println("сосед неисследованной ячейки:" + roomMap[it.first])*/
                    if (roomMap[it.first] is Wormhole) {
                        val wormhole = roomMap[it.first]
                        println("wormholes out:" + outputWormholeToOutputLocation)
                        println("wormholes in:" + outputWormholeToInputLocation)
                    }
                    it.first in traces.keys
                }.minByOrNull { traces[it.first]!!.size }
            if (bestNeighbourToDirection != null) {
                tracesSet.add(traces[bestNeighbourToDirection.first]!!)
            }
        }
        println(tracesSet)
        return tracesSet.minByOrNull { it.size }
    }

    //если был найден wormHole, который ранее уже встречался, то стоит объединить исследованную область
    fun updateMapByWormHole(inputLocation: Location, wormhole: Wormhole) {
        val oldLocation = outputWormholeToInputLocation[wormhole]!!

        if (oldLocation != inputLocation) {
            println("REBUILDING!!")
            val deltaX = oldLocation.x - inputLocation.x
            val deltaY = oldLocation.y - inputLocation.y
            println("DX:$deltaX DY:$deltaY")

            val oldRoomSubMap = roomMap.filter {
                (it.key.x in (inputLocation.x - 500)..(inputLocation.x + 500)) &&
                        (it.key.y in (inputLocation.y - 500)..(inputLocation.y + 500))
            }

            oldRoomSubMap.forEach {
                roomMap.remove(it.key)
                val newLocation = Location(it.key.x + deltaX, it.key.y + deltaY)
                if (it.value is Exit) {
                    println("exit moved from " + it.key + " to " + newLocation)
                }
                if (roomMap[newLocation] == null) {
                    roomMap[newLocation] = it.value
                } else if (roomMap[newLocation] != it.value) {
                    error("Wrong labyrinth!")
                }
            }
            outputWormholeToOutputLocation.update(inputLocation, deltaX, deltaY)
            outputWormholeToInputLocation.update(inputLocation, deltaX, deltaY)
        }
    }

    private fun MutableMap<Wormhole, Location>.update(inputLocation: Location, deltaX: Int, deltaY: Int) {
        val newMap = mutableMapOf<Wormhole, Location>()
        this.forEach {
            if ((it.value.x in (inputLocation.x - 500)..(inputLocation.x + 500)) &&
                (it.value.y in (inputLocation.y - 500)..(inputLocation.y + 500))
            ) {
                newMap[it.key] = Location(it.value.x + deltaX, it.value.y + deltaY)
            }
        }
        newMap.forEach { this[it.key] = it.value }
    }


    //поиск наилучших маршрутов по исследованным ячейкам
    fun findTraces(sourceLocation: Location): Map<Location, List<Move>> {
        val mapOfTraces = mutableMapOf<Location, List<Move>>()
        var setOfCurrentLocations = setOf<Location>(sourceLocation)
        var wormholesOutputToInputMap = mutableMapOf<Location, Location>()
        while (setOfCurrentLocations.isNotEmpty()) {
            val newSetOfLocation = mutableSetOf<Location>()
            setOfCurrentLocations.forEach { currentLocation ->
                //если текущая позиция является точкой выхода из кротовой норы,
                // то у текущей ячейки источниками могут быть только соседи входной точки в кротовую нору
                val neighbours = Direction.values().map { it + currentLocation }
                val sourceNeighboursToDirection =
                    if (wormholesOutputToInputMap.containsKey(currentLocation)) {
                        Direction.values().map { it + wormholesOutputToInputMap[currentLocation]!! to it.turnBack() }
                    } else {
                        Direction.values().map { it + currentLocation to it.turnBack() }
                    }

                //возможным источником может быть любой сосед>
                //следующей ячейкой может быть любая,
                //если это вход в кротовую нору то мы рассматриваем в качестве следующей точку выхода
                //при этом делаем
                newSetOfLocation.addAll(neighbours.filter {
                    roomMap[it] is Empty || roomMap[it] is WithContent || roomMap[it] is Entrance
                            || roomMap[it] is Exit || roomMap[it] is Wormhole
                }.map {
                    if (roomMap[it] is Wormhole) {
                        val output = outputWormholeToOutputLocation[roomMap[it]]!!
                        val input = it
                        wormholesOutputToInputMap[output] = input
                        output
                    } else {
                        it
                    }
                }
                )
                val bestNeighbourToDirection =
                    sourceNeighboursToDirection.filter { mapOfTraces.containsKey(it.first) }
                        .minByOrNull { mapOfTraces[it.first]!!.size }
                if (bestNeighbourToDirection == null) { //если это первая ячейка
                    mapOfTraces[currentLocation] = listOf<Move>()
                } else {
                    //assert(!mapOfTraces.containsKey(currentLocation))
                    val newTrace =
                        mapOfTraces[bestNeighbourToDirection.first]!! + WalkMove(bestNeighbourToDirection.second)
                    mapOfTraces[currentLocation] = newTrace
                }
            }
            setOfCurrentLocations = newSetOfLocation.filter {
                !mapOfTraces.containsKey(it) || (roomMap[it] is Wormhole && !mapOfTraces.containsKey(
                    outputWormholeToOutputLocation[roomMap[it]]
                ))
            }.toSet()
        }
        return mapOfTraces
    }

    fun findDirs(sourceLocation: Location): Map<Location, List<Pair<Direction, Location>>> {
        val mapOfTraces = mutableMapOf<Location, List<Pair<Direction, Location>>>()
        var setOfCurrentLocations = setOf<Location>(sourceLocation)
        var wormholesOutputToInputMap = mutableMapOf<Location, Location>()
        while (setOfCurrentLocations.isNotEmpty()) {
            val newSetOfLocation = mutableSetOf<Location>()
            setOfCurrentLocations.forEach { currentLocation ->

                //если текущая позиция является точкой выхода из кротовой норы,
                // то у текущей ячейки источниками могут быть только соседи входной точки в кротовую нору
                val neighbours = Direction.values().map { it + currentLocation }
                val sourceNeighboursToDirection =
                    if (wormholesOutputToInputMap.containsKey(currentLocation)) {
                        Direction.values().map { it + wormholesOutputToInputMap[currentLocation]!! to it.turnBack() }
                    } else {
                        Direction.values().map { it + currentLocation to it.turnBack() }
                    }

                //возможным источником может быть любой сосед>
                //следующей ячейкой может быть любая,
                //если это вход в кротовую нору то мы рассматриваем в качестве следующей точку выхода
                //при этом делаем
                newSetOfLocation.addAll(neighbours.filter {
                    roomMap[it] is Empty || roomMap[it] is WithContent || roomMap[it] is Entrance
                            || roomMap[it] is Exit || roomMap[it] is Wormhole
                }.map {
                    if (roomMap[it] is Wormhole) {
                        val output = outputWormholeToOutputLocation[roomMap[it]]!!
                        val input = it
                        wormholesOutputToInputMap[output] = input
                        output
                    } else {
                        it
                    }
                }
                )
                val bestNeighbourToDirection =
                    sourceNeighboursToDirection.filter { mapOfTraces.containsKey(it.first) }
                        .minByOrNull { mapOfTraces[it.first]!!.size }
                if (bestNeighbourToDirection == null) { //если это первая ячейка
                    mapOfTraces[currentLocation] = listOf<Pair<Direction, Location>>()
                } else {
                    //assert(!mapOfTraces.containsKey(currentLocation))
                    val newTrace =
                        mapOfTraces[bestNeighbourToDirection.first]!! + (bestNeighbourToDirection.second to currentLocation)
                    mapOfTraces[currentLocation] = newTrace
                }
            }
            setOfCurrentLocations = newSetOfLocation.filter {
                !mapOfTraces.containsKey(it) || (roomMap[it] is Wormhole && !mapOfTraces.containsKey(
                    outputWormholeToOutputLocation[roomMap[it]]
                ))
            }.toSet()
        }
        return mapOfTraces
    }
}

fun Location.getDistance(other: Location): Double {
    val a = this.x - other.x
    val b = this.y - other.y
    return sqrt((a * a + b * b).toDouble())
}

fun Location.getAverageDistance(listLocations: Iterable<Location>): Double {
    return listLocations.map { it.getDistance(this) }.average()
}



